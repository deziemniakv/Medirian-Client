import { create } from 'zustand';
import type {
  Account,
  AppInfo,
  ChangelogEntry,
  DiscordStatus,
  LauncherUpdateStatus,
  GameState,
  LaunchProfile,
  LauncherSettings,
  ReleaseState,
  SystemInfo,
  TargetStatus
} from '../../common/types';
import { invoke, on } from './api';

export type Page = 'home' | 'profiles' | 'changelog' | 'settings';

interface State {
  ready: boolean;
  page: Page;
  settings: LauncherSettings | null;
  profiles: LaunchProfile[];
  releases: ReleaseState | null;
  statuses: TargetStatus[];
  changelog: ChangelogEntry[];
  game: GameState;
  log: string[];
  account: Account | null;
  discord: DiscordStatus;
  launcherUpdate: LauncherUpdateStatus;
  app: AppInfo | null;
  system: SystemInfo | null;
  accountDialog: boolean;
  logOpen: boolean;

  init(): Promise<void>;
  navigate(page: Page): void;
  updateSettings(patch: Partial<LauncherSettings>): Promise<void>;
  refreshReleases(force?: boolean): Promise<void>;
  setProfiles(profiles: LaunchProfile[]): void;
  selectProfile(id: string): Promise<void>;
  launch(): Promise<void>;
  setAccountDialog(open: boolean): void;
  setLogOpen(open: boolean): void;
}

const LOG_LIMIT = 3000;
let subscribed = false;

export const useStore = create<State>((set, get) => ({
  ready: false,
  page: 'home',
  settings: null,
  profiles: [],
  releases: null,
  statuses: [],
  changelog: [],
  game: { state: 'idle' },
  log: [],
  account: null,
  discord: { state: 'disabled' },
  launcherUpdate: { state: 'unsupported' },
  app: null,
  system: null,
  accountDialog: false,
  logOpen: false,

  async init() {
    if (!subscribed) {
      subscribed = true;
      subscribe(set, get);
    }
    const [settings, profiles, game, account, app, system, changelog, log, discord, launcherUpdate] = await Promise.all([
      invoke('settings:get'),
      invoke('profiles:list'),
      invoke('game:state'),
      invoke('account:get'),
      invoke('app:info'),
      invoke('system:info'),
      invoke('changelog:get'),
      invoke('game:log'),
      invoke('discord:status'),
      invoke('launcherUpdate:status')
    ]);
    set({ settings, profiles, game, account, app, system, changelog, log, discord, launcherUpdate, ready: true });
    void get().refreshReleases();
  },

  navigate(page) {
    set({ page });
  },

  async updateSettings(patch) {
    const settings = await invoke('settings:update', patch);
    set({ settings });
  },

  async refreshReleases(force = false) {
    const releases = await invoke('releases:get', force);
    const statuses = releases.manifest ? await invoke('targets:status') : [];
    set({ releases, statuses });
  },

  setProfiles(profiles) {
    set({ profiles });
  },

  async selectProfile(id) {
    await get().updateSettings({ selectedProfileId: id });
  },

  async launch() {
    const profile = selectedProfile(get());
    if (profile) {
      set({ log: [] });
      await invoke('game:launch', profile.id);
    }
  },

  setAccountDialog(open) {
    set({ accountDialog: open });
  },

  setLogOpen(open) {
    set({ logOpen: open });
  }
}));

function subscribe(set: (partial: Partial<State> | ((state: State) => Partial<State>)) => void, get: () => State): void {
  on('game:state', (game) => {
    set({ game });
    if (game.state === 'exited' || game.state === 'running') {
      void get().refreshReleases();
    }
  });
  on('game:log', (lines) => set((state) => ({ log: [...state.log, ...lines].slice(-LOG_LIMIT) })));
  on('account:changed', (account) => set({ account }));
  on('discord:status', (discord) => set({ discord }));
  on('launcherUpdate:status', (launcherUpdate) => set({ launcherUpdate }));
}

export function selectedProfile(state: Pick<State, 'profiles' | 'settings'>): LaunchProfile | undefined {
  return state.profiles.find((p) => p.id === state.settings?.selectedProfileId) ?? state.profiles[0];
}
