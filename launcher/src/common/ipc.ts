// The complete main ↔ renderer contract. The preload script exposes exactly these channels.

import type {
  Account,
  AppInfo,
  ChangelogEntry,
  DeviceCodeInfo,
  DiskUsage,
  GameState,
  JavaInstall,
  LaunchProfile,
  LauncherSettings,
  LoginResult,
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

  'system:info': () => SystemInfo;
  'setup:run': () => SetupCheck[];
  'setup:fix': (id: SetupCheck['id']) => SetupCheck[];

  'shell:open': (target: OpenTarget, targetId?: string) => void;
  'shell:openExternal': (url: string) => void;
}

/** Push events: main → renderer. */
export interface EventApi {
  'game:state': GameState;
  'game:log': string[];
  'account:changed': Account | null;
  'account:loginResult': LoginResult;
}

export type InvokeChannel = keyof InvokeApi;
export type EventChannel = keyof EventApi;

export const INVOKE_CHANNELS: InvokeChannel[] = [
  'app:info', 'window:minimize', 'window:close',
  'settings:get', 'settings:update',
  'profiles:list', 'profiles:create', 'profiles:save', 'profiles:delete', 'client:configProfiles',
  'releases:get', 'targets:status', 'changelog:get',
  'game:state', 'game:launch', 'game:kill', 'game:log',
  'install:repair', 'install:clearCache', 'install:diskUsage',
  'java:detect',
  'account:get', 'account:loginStart', 'account:loginCancel', 'account:logout', 'account:offline', 'account:offlineAllowed',
  'system:info', 'setup:run', 'setup:fix',
  'shell:open', 'shell:openExternal'
];

export const EVENT_CHANNELS: EventChannel[] = ['game:state', 'game:log', 'account:changed', 'account:loginResult'];

/** Shape of `window.meridian` exposed by the preload script. */
export interface MeridianBridge {
  invoke<K extends InvokeChannel>(channel: K, ...args: Parameters<InvokeApi[K]>): Promise<Awaited<ReturnType<InvokeApi[K]>>>;
  on<K extends EventChannel>(channel: K, listener: (payload: EventApi[K]) => void): () => void;
}
