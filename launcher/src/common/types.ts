// Types shared by the main process and the renderer. Keep this file free of Node/DOM imports.

export type Language = 'en' | 'pl';
export type ThemeMode = 'auto' | 'default' | 'halloween';
export type AfterLaunch = 'keep' | 'minimize';
export type UpdateChannel = 'stable' | 'local';

export interface LauncherSettings {
  version: 1;
  language: Language;
  theme: ThemeMode;
  afterLaunch: AfterLaunch;
  updateChannel: UpdateChannel;
  /** URL of the release manifest for the stable channel ('' = not configured yet). */
  manifestUrl: string;
  /** Directory containing release-manifest.json for the local channel. */
  localDistributionDir: string;
  concurrentDownloads: number;
  /** Copy assets from an existing .minecraft installation instead of downloading them. */
  reuseMinecraftAssets: boolean;
  setupCompleted: boolean;
  selectedProfileId: string | null;
  /** Developer override for the Azure application id used for Microsoft login. */
  msaClientId: string;
}

export interface Resolution {
  width: number;
  height: number;
}

/** A launch profile ("installation"): which Meridian target to start and how. */
export interface LaunchProfile {
  id: string;
  name: string;
  targetId: string;
  memoryMb: number;
  /** null = automatic (Mojang runtime matching the target). */
  javaPath: string | null;
  jvmArgs: string;
  resolution: Resolution | null;
  /** Meridian client configuration profile to start with (null = last used in game). */
  configProfile: string | null;
  createdAt: number;
  lastPlayed: number | null;
}

// ---------------------------------------------------------------- releases

export type LoaderType = 'fabric' | 'legacy-fabric';

export interface ReleaseArtifact {
  /** File name relative to the manifest location (local channel) … */
  file?: string;
  /** … or absolute download URL (stable channel). */
  url?: string;
  sha1: string;
  size: number;
}

export interface ReleaseTarget {
  id: string;
  displayName: string;
  minecraftVersion: string;
  branch: 'legacy' | 'modern';
  loader: { type: LoaderType; version: string };
  java: { component: string; majorVersion: number };
  artifact: ReleaseArtifact;
  recommended?: boolean;
  tags?: string[];
  description?: string;
}

export interface ChangelogEntry {
  version: string;
  date: string;
  title: string;
  items: string[];
}

export interface ReleaseManifest {
  schema: 1;
  channel: string;
  generatedAt: string;
  client: { version: string };
  launcher?: { version: string; minimum?: string };
  targets: ReleaseTarget[];
}

export interface ReleaseState {
  manifest: ReleaseManifest | null;
  error: string | null;
  source: string;
  checkedAt: number;
}

export interface TargetStatus {
  targetId: string;
  installedVersion: string | null;
  latestVersion: string | null;
  updateAvailable: boolean;
  minecraftInstalled: boolean;
}

// ---------------------------------------------------------------- tasks & game

export type TaskPhase = 'manifest' | 'java' | 'version' | 'libraries' | 'natives' | 'assets' | 'loader' | 'client' | 'verify' | 'launch';

export interface TaskProgress {
  phase: TaskPhase;
  /** Human readable step, e.g. "Downloading assets". */
  label: string;
  done: number;
  total: number;
  bytesDone?: number;
  bytesTotal?: number;
}

export interface BridgeStatus {
  connected: boolean;
  state?: 'menu' | 'singleplayer' | 'multiplayer';
  server?: string;
  profile?: string;
  clientVersion?: string;
}

export type GameState =
  | { state: 'idle' }
  | { state: 'preparing'; profileId: string; progress: TaskProgress }
  | { state: 'running'; profileId: string; targetId: string; pid: number; since: number; bridge: BridgeStatus }
  | { state: 'exited'; profileId: string; code: number | null; crashed: boolean; lastLines: string[] }
  | { state: 'error'; profileId: string; message: string };

export interface RepairReport {
  targetId: string;
  checkedFiles: number;
  repairedFiles: number;
  durationMs: number;
}

export interface DiskUsage {
  runtimeBytes: number;
  gameBytes: number;
  clientsBytes: number;
  cacheBytes: number;
}

// ---------------------------------------------------------------- accounts

export interface Account {
  uuid: string;
  name: string;
  type: 'microsoft' | 'offline';
}

export interface DeviceCodeInfo {
  userCode: string;
  verificationUri: string;
  expiresInSec: number;
}

export type LoginResult = { ok: true; account: Account } | { ok: false; error: string };

// ---------------------------------------------------------------- system & setup

export interface JavaInstall {
  path: string;
  version: string;
  major: number;
  arch: string;
  vendor: string;
  source: 'meridian' | 'system';
}

export interface SystemInfo {
  platform: string;
  arch: string;
  osRelease: string;
  totalMemoryMb: number;
  freeMemoryMb: number;
  freeDiskMb: number | null;
  home: string;
  minecraftDir: string | null;
}

export type CheckStatus = 'pending' | 'ok' | 'warn' | 'error';

export interface SetupCheck {
  id: 'system' | 'memory' | 'disk' | 'network' | 'directory' | 'java' | 'minecraft' | 'config' | 'integrity';
  status: CheckStatus;
  detail: string;
  fix?: 'create-directory' | 'retry' | 'repair';
}

export interface AppInfo {
  version: string;
  electron: string;
  chrome: string;
  node: string;
  packaged: boolean;
  home: string;
  platform: string;
}

export type OpenTarget = 'home' | 'logs' | 'instance' | 'screenshots';
