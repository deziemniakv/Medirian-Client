import { spawn, type ChildProcess } from 'node:child_process';
import { createWriteStream, type WriteStream } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import type { GameState, TaskProgress } from '../../common/types';
import type { AccountService } from '../auth/accounts';
import { log } from '../core/log';
import type { MeridianPaths } from '../core/paths';
import type { Installer } from '../install/installer';
import type { ProfileStore } from '../profiles/profiles';
import type { UpdateService } from '../updates/updates';
import { ClientBridge } from './bridge';

const LOG_LIMIT = 3000;

/** Owns the lifecycle of the single running game: prepare → spawn → monitor → exit. */
export class GameService {
  private state: GameState = { state: 'idle' };
  private process: ChildProcess | null = null;
  private bridge: ClientBridge | null = null;
  private lines: string[] = [];
  private pendingLines: string[] = [];
  private flushTimer: NodeJS.Timeout | null = null;
  private logFile: WriteStream | null = null;
  private killed = false;

  constructor(
    private readonly paths: MeridianPaths,
    private readonly installer: Installer,
    private readonly updates: UpdateService,
    private readonly profiles: ProfileStore,
    private readonly accounts: AccountService,
    private readonly launcherVersion: string,
    private readonly emitState: (state: GameState) => void,
    private readonly emitLog: (lines: string[]) => void,
    private readonly onStarted: () => void
  ) {}

  current(): GameState {
    return this.state;
  }

  log(): string[] {
    return this.lines;
  }

  private setState(state: GameState): void {
    this.state = state;
    this.emitState(state);
  }

  async launch(profileId: string): Promise<void> {
    if (this.state.state === 'preparing' || this.state.state === 'running') {
      throw new Error('Minecraft is already running.');
    }
    const profile = this.profiles.get(profileId);
    if (!profile) {
      throw new Error('Profile not found.');
    }
    const progress = (p: TaskProgress) => this.setState({ state: 'preparing', profileId, progress: p });
    progress({ phase: 'manifest', label: 'Checking for updates', done: 0, total: 1 });

    try {
      const releases = await this.updates.refresh(false);
      const target = this.updates.target(profile.targetId);
      if (!target) {
        throw new Error(releases.error ?? `Meridian for ${profile.targetId} is not available in the current release channel.`);
      }
      const session = await this.accounts.session();
      this.bridge = new ClientBridge((bridge) => {
        if (this.state.state === 'running') {
          this.setState({ ...this.state, bridge });
        }
      });
      await this.bridge.start();
      const plan = await this.installer.prepare(
        { profile, target, session, launcherVersion: this.launcherVersion, bridge: { port: this.bridge.port, token: this.bridge.token } },
        progress
      );
      await this.spawn(profileId, target.id, plan.java, plan.args, plan.gameDir, session.accessToken);
      await this.profiles.markPlayed(profileId);
    } catch (error) {
      this.bridge?.close();
      this.bridge = null;
      const message = error instanceof Error ? error.message : String(error);
      log.error(`Launch failed: ${message}`, error);
      this.setState({ state: 'error', profileId, message });
    }
  }

  private async spawn(profileId: string, targetId: string, java: string, args: string[], gameDir: string, token: string): Promise<void> {
    await mkdir(this.paths.logs, { recursive: true });
    this.lines = [];
    this.logFile = createWriteStream(join(this.paths.logs, `game-${targetId}.log`), { flags: 'w' });
    this.killed = false;
    // never write the access token to logs
    log.info(`Launching ${targetId}: ${java} ${args.map((a) => (a === token ? '<token>' : a)).join(' ').slice(0, 4000)}`);
    // detached: closing the launcher must never take the running game down with it
    const child = spawn(java, args, { cwd: gameDir, windowsHide: true, detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
    this.process = child;
    const onData = (chunk: Buffer) => this.appendLog(chunk.toString('utf8').split(/\r?\n/).filter(Boolean), token);
    child.stdout?.on('data', onData);
    child.stderr?.on('data', onData);
    child.once('error', (error) => {
      this.setState({ state: 'error', profileId, message: `Could not start Java: ${error.message}` });
    });
    child.once('spawn', () => {
      this.setState({ state: 'running', profileId, targetId, pid: child.pid ?? 0, since: Date.now(), bridge: { connected: false } });
      this.onStarted();
    });
    child.once('exit', (code, signal) => {
      this.flushLog();
      this.logFile?.end();
      this.logFile = null;
      this.bridge?.close();
      this.bridge = null;
      this.process = null;
      // a signal means the process was terminated from outside, not a game crash
      const crashed = !this.killed && signal === null && code !== 0 && code !== null;
      log.info(`Minecraft exited with code ${code}${crashed ? ' (crash)' : ''}`);
      this.setState({ state: 'exited', profileId, code, crashed, lastLines: this.lines.slice(-40) });
    });
  }

  private appendLog(lines: string[], token: string): void {
    for (const raw of lines) {
      const line = token && token.length > 8 ? raw.split(token).join('<token>') : raw;
      this.lines.push(line);
      this.pendingLines.push(line);
      this.logFile?.write(line + '\n');
    }
    if (this.lines.length > LOG_LIMIT) {
      this.lines.splice(0, this.lines.length - LOG_LIMIT);
    }
    if (!this.flushTimer) {
      // batch log lines to the renderer at most 5 times per second
      this.flushTimer = setTimeout(() => this.flushLog(), 200);
    }
  }

  private flushLog(): void {
    if (this.flushTimer) {
      clearTimeout(this.flushTimer);
      this.flushTimer = null;
    }
    if (this.pendingLines.length > 0) {
      this.emitLog(this.pendingLines);
      this.pendingLines = [];
    }
  }

  kill(): void {
    if (this.process) {
      this.killed = true;
      this.process.kill();
    }
  }

  notify(title: string, message: string): void {
    this.bridge?.notify(title, message);
  }
}
