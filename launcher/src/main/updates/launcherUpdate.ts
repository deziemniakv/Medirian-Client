import type { LauncherUpdateStatus } from '../../common/types';

/** The part of electron-updater's AppUpdater the launcher uses (an interface, so tests can fake it). */
export interface Updater {
  autoDownload: boolean;
  autoInstallOnAppQuit: boolean;
  on(event: string, listener: (...args: any[]) => void): unknown;
  checkForUpdates(): Promise<unknown>;
  quitAndInstall(isSilent?: boolean, isForceRunAfter?: boolean): void;
}

interface Logger {
  info(message: string): void;
  warn(message: string): void;
}

export const CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000;

const message = (error: unknown): string => (error instanceof Error ? error.message : String(error));

/**
 * Updates the launcher itself (electron-updater). Installed builds only: electron-builder writes
 * app-update.yml when a publish target is configured (the release workflow does), so development
 * runs and local `npm run dist` builds report "unsupported" and the UI shows nothing.
 *
 * Updates are downloaded in the background; the new version is installed on the next quit, or
 * immediately when the user picks "Restart to update".
 */
export class LauncherUpdateService {
  private readonly updater: Updater | null;
  private readonly emit: (status: LauncherUpdateStatus) => void;
  private readonly log: Logger;
  private status: LauncherUpdateStatus;
  private timer: ReturnType<typeof setInterval> | null = null;

  // plain fields instead of parameter properties: the tests run with Node's type stripping
  constructor(updater: Updater | null, emit: (status: LauncherUpdateStatus) => void, log: Logger) {
    this.updater = updater;
    this.emit = emit;
    this.log = log;
    this.status = { state: updater ? 'idle' : 'unsupported' };
    if (!updater) {
      return;
    }
    updater.autoDownload = true;
    updater.autoInstallOnAppQuit = true;
    updater.on('checking-for-update', () => this.set({ state: 'checking' }));
    updater.on('update-available', (info: { version?: string }) => {
      this.log.info(`Launcher update ${info?.version} available, downloading`);
      this.set({ state: 'downloading', version: info?.version, percent: 0 });
    });
    updater.on('update-not-available', () => this.set({ state: 'latest', checkedAt: Date.now() }));
    updater.on('download-progress', (progress: { percent?: number }) =>
      this.set({ state: 'downloading', version: this.status.version, percent: Math.floor(progress?.percent ?? 0) }));
    updater.on('update-downloaded', (info: { version?: string }) => {
      this.log.info(`Launcher update ${info?.version} downloaded`);
      this.set({ state: 'ready', version: info?.version });
    });
    updater.on('error', (error: unknown) => this.fail(error));
  }

  current(): LauncherUpdateStatus {
    return this.status;
  }

  /** First check right away, then periodically. */
  start(): void {
    if (!this.updater || this.timer) {
      return;
    }
    void this.check();
    this.timer = setInterval(() => void this.check(), CHECK_INTERVAL_MS);
    this.timer.unref?.();
  }

  async check(): Promise<LauncherUpdateStatus> {
    const busy = this.status.state === 'checking' || this.status.state === 'downloading' || this.status.state === 'ready';
    if (!this.updater || busy) {
      return this.status;
    }
    try {
      await this.updater.checkForUpdates();
    } catch (error) {
      this.fail(error);
    }
    return this.status;
  }

  /**
   * Quits, installs the downloaded update without any installer window (into the same folder,
   * for the same user) and starts the new version (no-op until one is ready).
   */
  install(): void {
    if (this.updater && this.status.state === 'ready') {
      this.updater.quitAndInstall(true, true);
    }
  }

  dispose(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private fail(error: unknown): void {
    this.log.warn(`Launcher update failed: ${message(error)}`);
    this.set({ state: 'error', error: message(error), checkedAt: Date.now() });
  }

  private set(status: LauncherUpdateStatus): void {
    this.status = status;
    this.emit(status);
  }
}
