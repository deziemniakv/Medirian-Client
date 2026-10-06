// The complete main ↔ renderer contract. The preload script exposes exactly these channels.

import type {
  Account,
  InstalledModsState,
  ModCategory,
  ModDetails,
  ModInstallPlan,
  ModSearchQuery,
  ModSearchResult,
  ModSource,
  ModSourceInfo,
  ModTask,
  AppInfo,
  ChangelogEntry,
  DeviceCodeInfo,
  DiscordStatus,
  DiskUsage,
  GameState,
  JavaInstall,
  LauncherUpdateStatus,
  LaunchProfile,
  LauncherSettings,
  LoginResult,
  PlayerSkin,
  OpenTarget,
  ReleaseState,
  RepairReport,
  SetupCheck,
  SystemInfo,
  TargetStatus
} from './types';

/** Request/response channels: renderer calls `invoke(channel, ...args)`. */
export interface InvokeApi {
  'app:info': () => AppInfo;
  'window:minimize': () => void;
  'window:close': () => void;

  'settings:get': () => LauncherSettings;
  'settings:update': (patch: Partial<LauncherSettings>) => LauncherSettings;

  'profiles:list': () => LaunchProfile[];
  'profiles:create': (base: Partial<LaunchProfile>) => LaunchProfile;
  'profiles:save': (profile: LaunchProfile) => LaunchProfile[];
  'profiles:delete': (id: string) => LaunchProfile[];
  'profiles:duplicate': (id: string, name: string) => LaunchProfile;
  'client:configProfiles': () => string[];

  'releases:get': (refresh: boolean) => ReleaseState;
  'targets:status': () => TargetStatus[];
  'changelog:get': () => ChangelogEntry[];

  'game:state': () => GameState;
  'game:launch': (profileId: string) => void;
  'game:kill': () => void;
  'game:log': () => string[];

  'install:repair': (targetId: string) => RepairReport;
  'install:clearCache': () => number;
  'install:diskUsage': () => DiskUsage;

  'java:detect': () => JavaInstall[];

  'account:get': () => Account | null;
  'account:loginStart': () => DeviceCodeInfo;
  'account:loginCancel': () => void;
  'account:logout': () => void;
  'account:offline': (name: string) => Account;
  'account:offlineAllowed': () => boolean;
  'skin:get': () => PlayerSkin;
  'skin:refresh': () => PlayerSkin;

  'system:info': () => SystemInfo;
  'setup:run': () => SetupCheck[];
  'setup:fix': (id: SetupCheck['id']) => SetupCheck[];

  'discord:status': () => DiscordStatus;

  'launcherUpdate:status': () => LauncherUpdateStatus;
  'launcherUpdate:check': () => LauncherUpdateStatus;
  'launcherUpdate:install': () => void;

  'mods:sources': () => ModSourceInfo[];
  'mods:search': (query: ModSearchQuery) => ModSearchResult;
  'mods:categories': (source: ModSource) => ModCategory[];
  'mods:details': (source: ModSource, projectId: string, profileId: string) => ModDetails;
  'mods:plan': (profileId: string, source: ModSource, projectId: string, versionId?: string) => ModInstallPlan;
  'mods:install': (profileId: string, source: ModSource, projectId: string, versionId?: string) => InstalledModsState;
  'mods:installed': (profileId: string) => InstalledModsState;
  'mods:setEnabled': (profileId: string, file: string, enabled: boolean) => InstalledModsState;
  'mods:remove': (profileId: string, file: string) => InstalledModsState;
  'mods:checkUpdates': (profileId: string) => InstalledModsState;
  'mods:update': (profileId: string, file: string) => InstalledModsState;

  'shell:open': (target: OpenTarget, profileId?: string) => void;
  'shell:openExternal': (url: string) => void;
}

/** Push events: main → renderer. */
export interface EventApi {
  'game:state': GameState;
  'game:log': string[];
  'account:changed': Account | null;
  'account:loginResult': LoginResult;
  'discord:status': DiscordStatus;
  'launcherUpdate:status': LauncherUpdateStatus;
  'mods:task': ModTask | null;
  'skin:changed': PlayerSkin;
}

export type InvokeChannel = keyof InvokeApi;
export type EventChannel = keyof EventApi;

export const INVOKE_CHANNELS: InvokeChannel[] = [
  'app:info', 'window:minimize', 'window:close',
  'settings:get', 'settings:update',
  'profiles:list', 'profiles:create', 'profiles:save', 'profiles:delete', 'profiles:duplicate', 'client:configProfiles',
  'releases:get', 'targets:status', 'changelog:get',
  'game:state', 'game:launch', 'game:kill', 'game:log',
  'install:repair', 'install:clearCache', 'install:diskUsage',
  'java:detect',
  'account:get', 'account:loginStart', 'account:loginCancel', 'account:logout', 'account:offline', 'account:offlineAllowed',
  'skin:get', 'skin:refresh',
  'system:info', 'setup:run', 'setup:fix',
  'discord:status',
  'launcherUpdate:status', 'launcherUpdate:check', 'launcherUpdate:install',
  'mods:sources', 'mods:search', 'mods:categories', 'mods:details', 'mods:plan', 'mods:install', 'mods:installed',
  'mods:setEnabled', 'mods:remove', 'mods:checkUpdates', 'mods:update',
  'shell:open', 'shell:openExternal'
];

export const EVENT_CHANNELS: EventChannel[] = ['game:state', 'game:log', 'account:changed', 'account:loginResult', 'discord:status',
  'launcherUpdate:status', 'mods:task', 'skin:changed'];

/** Shape of `window.medirian` exposed by the preload script. */
export interface MedirianBridge {
  invoke<K extends InvokeChannel>(channel: K, ...args: Parameters<InvokeApi[K]>): Promise<Awaited<ReturnType<InvokeApi[K]>>>;
  on<K extends EventChannel>(channel: K, listener: (payload: EventApi[K]) => void): () => void;
}
