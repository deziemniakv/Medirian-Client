import type { DiscordStatus, GameState, Language } from '../../common/types';
// explicit extension: the module is also loaded by `node --test` (see test/discord.test.ts)
import { DiscordIpcClient, DiscordUnavailableError, type Activity } from './ipc.ts';

/** Rich presence art asset uploaded to the Discord application (Rich Presence → Art Assets). */
export const LARGE_IMAGE = 'meridian';

const TEXT = {
  en: {
    launcher: 'In the launcher',
    starting: (game: string) => `Starting ${game}`,
    preparing: 'Preparing the game',
    loading: 'Loading…',
    menu: 'In the menus',
    singleplayer: 'Singleplayer',
    multiplayer: 'Multiplayer',
    playingOn: (server: string) => `Playing on ${server}`
  },
  de: {
    launcher: 'Im Launcher',
    starting: (game: string) => `Startet ${game}`,
    preparing: 'Bereitet das Spiel vor',
    loading: 'Lädt…',
    menu: 'In den Menüs',
    singleplayer: 'Einzelspieler',
    multiplayer: 'Mehrspieler',
    playingOn: (server: string) => `Spielt auf ${server}`
  },
  es: {
    launcher: 'En el launcher',
    starting: (game: string) => `Iniciando ${game}`,
    preparing: 'Preparando el juego',
    loading: 'Cargando…',
    menu: 'En los menús',
    singleplayer: 'Un jugador',
    multiplayer: 'Multijugador',
    playingOn: (server: string) => `Jugando en ${server}`
  },
  pl: {
    launcher: 'W launcherze',
    starting: (game: string) => `Uruchamia ${game}`,
    preparing: 'Przygotowuje grę',
    loading: 'Wczytywanie…',
    menu: 'W menu',
    singleplayer: 'Gra jednoosobowa',
    multiplayer: 'Gra wieloosobowa',
    playingOn: (server: string) => `Gra na ${server}`
  }
} as const;

export interface PresenceContext {
  game: GameState;
  /** Display name of a release target, e.g. "Minecraft 1.8.9". */
  targetName: (targetId: string) => string | null;
  /** Release target of a launch profile. */
  profileTarget: (profileId: string) => string | null;
  /** Meridian version shown when the client has not reported its own. */
  version: string;
  language: Language;
  showServer: boolean;
  showInLauncher: boolean;
}

/** Discord limits activity strings to 2–128 characters. */
function fit(text: string): string {
  const trimmed = text.trim();
  return trimmed.length > 128 ? `${trimmed.slice(0, 127)}…` : trimmed.padEnd(2, ' ');
}

/** The activity for the current state, or null for none. Pure: unit-tested. */
export function buildActivity(context: PresenceContext): Activity | null {
  const text = TEXT[context.language] ?? TEXT.en;
  const game = context.game;
  const assets = (version: string) => ({ large_image: LARGE_IMAGE, large_text: fit(`Meridian Client ${version}`) });
  if (game.state === 'running') {
    const target = context.targetName(game.targetId) ?? `Minecraft ${game.targetId}`;
    const bridge = game.bridge;
    const details = bridge.profile ? `${target} · ${bridge.profile}` : target;
    let state: string = text.loading;
    if (bridge.state === 'menu') {
      state = text.menu;
    } else if (bridge.state === 'singleplayer') {
      state = text.singleplayer;
    } else if (bridge.state === 'multiplayer') {
      state = context.showServer && bridge.server ? text.playingOn(bridge.server.toLowerCase()) : text.multiplayer;
    }
    return {
      details: fit(details),
      state: fit(state),
      timestamps: { start: game.since },
      assets: assets(bridge.clientVersion || context.version),
      instance: false
    };
  }
  if (game.state === 'preparing') {
    const targetId = context.profileTarget(game.profileId);
    const target = (targetId && context.targetName(targetId)) || 'Minecraft';
    return { details: fit(text.starting(target)), state: fit(text.preparing), assets: assets(context.version), instance: false };
  }
  return context.showInLauncher ? { details: fit(text.launcher), assets: assets(context.version), instance: false } : null;
}

/** Discord allows 5 activity updates per 20 seconds. */
const MIN_UPDATE_INTERVAL_MS = 4000;
const RETRY_MS = 15_000;

export interface PresenceOptions {
  enabled: () => boolean;
  applicationId: () => string;
  activity: () => Activity | null;
  emitStatus: (status: DiscordStatus) => void;
  log: { info: (message: string) => void; warn: (message: string) => void };
  /** Creates the IPC client (tests pass a fake). */
  createClient?: () => DiscordIpcClient;
}

/**
 * Keeps the user's Discord activity in sync with the launcher and game. Connects when Discord
 * runs (re-trying while it does not), sends only changed activities and respects Discord's rate
 * limit (the newest activity always wins).
 */
export class DiscordPresenceService {
  private readonly options: PresenceOptions;
  private client: DiscordIpcClient | null = null;
  private clientAppId = '';
  /** Application id of the last connection attempt: a new id is tried at once, not after the retry delay. */
  private attemptedAppId = '';
  private connecting = false;
  private status: DiscordStatus = { state: 'disabled' };
  private sent: string | null = null;
  private lastSentAt = 0;
  private sendTimer: NodeJS.Timeout | null = null;
  private retryTimer: NodeJS.Timeout | null = null;
  private disposed = false;

  constructor(options: PresenceOptions) {
    this.options = options;
  }

  current(): DiscordStatus {
    return this.status;
  }

  /** Re-evaluates settings and the activity; call after game state or settings changes. */
  refresh(): void {
    if (this.disposed) {
      return;
    }
    const appId = this.options.applicationId().trim();
    if (!this.options.enabled()) {
      this.disconnect();
      this.setStatus({ state: 'disabled' });
      return;
    }
    if (!appId) {
      this.disconnect();
      this.setStatus({ state: 'unconfigured' });
      return;
    }
    if (this.client && this.clientAppId !== appId) {
      this.disconnect();
    }
    if (appId !== this.attemptedAppId && this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    if (this.client?.connected) {
      this.scheduleSend();
    } else {
      void this.connect(appId);
    }
  }

  dispose(): void {
    this.disposed = true;
    this.disconnect();
  }

  private async connect(appId: string): Promise<void> {
    if (this.connecting || this.retryTimer) {
      return;
    }
    this.connecting = true;
    this.attemptedAppId = appId;
    let stale = false;
    if (this.status.state !== 'unavailable') {
      this.setStatus({ state: 'connecting' });
    }
    const client = this.options.createClient ? this.options.createClient() : new DiscordIpcClient();
    try {
      const user = await client.connect(appId);
      if (this.disposed || appId !== this.options.applicationId().trim() || !this.options.enabled()) {
        client.close();
        stale = true;
        return;
      }
      this.client = client;
      this.clientAppId = appId;
      this.sent = null;
      client.onClose((reason) => {
        if (this.client === client) {
          this.client = null;
          this.options.log.info(`Discord connection closed: ${reason}`);
          this.setStatus({ state: 'unavailable' });
          this.scheduleRetry();
        }
      });
      this.options.log.info(`Connected to Discord as ${user.username}`);
      this.setStatus({ state: 'connected', user: user.global_name || user.username });
      this.scheduleSend();
    } catch (error) {
      if (error instanceof DiscordUnavailableError) {
        this.setStatus({ state: 'unavailable' });
      } else {
        const message = error instanceof Error ? error.message : String(error);
        this.options.log.warn(`Discord rich presence failed: ${message}`);
        this.setStatus({ state: 'error', error: message });
      }
      this.scheduleRetry();
    } finally {
      this.connecting = false;
      if (!this.disposed && (stale || appId !== this.options.applicationId().trim())) {
        // settings changed while connecting: start over with the current ones
        if (this.retryTimer) {
          clearTimeout(this.retryTimer);
          this.retryTimer = null;
        }
        setTimeout(() => this.refresh(), 0);
      }
    }
  }

  private scheduleRetry(): void {
    if (this.retryTimer || this.disposed) {
      return;
    }
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.refresh();
    }, RETRY_MS);
  }

  private scheduleSend(): void {
    if (this.sendTimer) {
      return;
    }
    const delay = Math.max(0, this.lastSentAt + MIN_UPDATE_INTERVAL_MS - Date.now());
    this.sendTimer = setTimeout(() => {
      this.sendTimer = null;
      void this.send();
    }, delay);
  }

  private async send(): Promise<void> {
    const client = this.client;
    if (!client?.connected) {
      return;
    }
    const activity = this.options.activity();
    const json = JSON.stringify(activity);
    if (json === this.sent) {
      return;
    }
    this.lastSentAt = Date.now();
    try {
      await client.setActivity(activity);
      this.sent = json;
    } catch (error) {
      this.options.log.warn(`Discord activity update failed: ${error instanceof Error ? error.message : String(error)}`);
    }
    // the state may have changed while sending
    if (this.client === client && JSON.stringify(this.options.activity()) !== this.sent) {
      this.scheduleSend();
    }
  }

  private disconnect(): void {
    if (this.sendTimer) {
      clearTimeout(this.sendTimer);
      this.sendTimer = null;
    }
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    // closing the connection clears the activity on Discord's side
    this.client?.close();
    this.client = null;
    this.sent = null;
  }

  private setStatus(status: DiscordStatus): void {
    if (JSON.stringify(status) !== JSON.stringify(this.status)) {
      this.status = status;
      this.options.emitStatus(status);
    }
  }
}
