// Types shared by the main process and the renderer. Keep this file free of Node/DOM imports.

export type Language = 'en' | 'pl' | 'de' | 'es';
export type AfterLaunch = 'keep' | 'minimize';
export type UpdateChannel = 'stable' | 'local';

export interface LauncherSettings {
  version: 1;
  language: Language;
  /** The night scene moves (fog, lights, a bat, parallax). */
  sceneMotion: boolean;
  /** Snow over the night from December to 6 January. */
  winterSnow: boolean;
  afterLaunch: AfterLaunch;
  /** stable = MEDIRIAN_MANIFEST_URL of this build; local = a distribution folder (development builds). */
  updateChannel: UpdateChannel;
  /** Directory containing release-manifest.json for the local channel. */
  localDistributionDir: string;
  concurrentDownloads: number;
  /** Copy assets from an existing .minecraft installation instead of downloading them. */
  reuseMinecraftAssets: boolean;
  setupCompleted: boolean;
  selectedProfileId: string | null;
  /** Show the running game on the user's Discord profile (Rich Presence). */
  discordPresence: boolean;
  /** Include the server address in the Discord activity. */
  discordShowServer: boolean;
  /** Also show an activity while only the launcher is open. */
  discordShowInLauncher: boolean;
}

export interface Resolution {
  width: number;
  height: number;
}

/** A launch profile ("installation"): which Medirian target to start and how. */
export interface LaunchProfile {
  id: string;
  name: string;
  targetId: string;
  memoryMb: number;
  /** null = automatic (Mojang runtime matching the target). */
  javaPath: string | null;
  jvmArgs: string;
  resolution: Resolution | null;
  /** Medirian client configuration profile to start with (null = last used in game). */
  configProfile: string | null;
  createdAt: number;
  lastPlayed: number | null;
  /** Folder of this profile's game directory in MEDIRIAN_HOME/profiles (chosen once, kept on rename). */
  directory: string;
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

/** Self-update of the launcher (electron-updater); "unsupported" in development and unpublished builds. */
export interface LauncherUpdateStatus {
  state: 'unsupported' | 'idle' | 'checking' | 'latest' | 'downloading' | 'ready' | 'error';
  version?: string;
  percent?: number;
  error?: string;
  checkedAt?: number;
}

export interface DiscordStatus {
  state: 'disabled' | 'unconfigured' | 'connecting' | 'connected' | 'unavailable' | 'error';
  /** Discord user while connected. */
  user?: string;
  error?: string;
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

/** The signed-in player's Minecraft skin. */
export interface PlayerSkin {
  uuid: string | null;
  name: string | null;
  /** data: URL of the 64×64 skin; null = Medirian's default skin (not signed in, or no skin set). */
  texture: string | null;
  model: 'classic' | 'slim';
  source: 'mojang' | 'default';
  updatedAt: number;
  /** Set when the last refresh failed (the cached skin is still shown). */
  error?: string;
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
  source: 'medirian' | 'system';
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
  /** What the owner configured in this build's .env (no values, only what is available). */
  config: BuildStatus;
}

/** Which owner-configured features this launcher build has (docs/OWNER_SETUP.md). */
export interface BuildStatus {
  microsoftLogin: boolean;
  discord: boolean;
  /** Host of Medirian Services, or null when not configured. */
  servicesHost: string | null;
  /** Host serving the release manifest, or null. */
  manifestHost: string | null;
  /** Rejected .env values (e.g. an address without HTTPS). */
  problems: string[];
}

export type OpenTarget = 'home' | 'logs' | 'instance' | 'mods' | 'screenshots';

// ---------------------------------------------------------------- profile codes

/** Why a profile code action failed (translated in the renderer: "share.error.<code>"). */
export type ShareError =
  | 'unconfigured' | 'invalid' | 'notFound' | 'expired' | 'tooMany' | 'forbidden' | 'offline' | 'noSettings' | 'running';

export type ShareResult<T> = { ok: true; value: T } | { ok: false; error: ShareError };

export interface ShareExport {
  /** MDN-XXXX-XXXX-XXXX */
  code: string;
  expiresAt: number;
  name: string;
}

/** What importing a code would bring in. */
export interface SharePreview {
  code: string;
  name: string;
  targetId: string;
  memoryMb: number;
  modules: number;
  enabledModules: number;
  gameOptions: number;
  hasClientSettings: boolean;
  expiresAt: number;
  /** A launch profile with the same name, which the import could replace. */
  existingProfileId: string | null;
}

export type ShareImportMode = 'new' | 'replace';

// ---------------------------------------------------------------- mods

/** Where an installed mod comes from: Modrinth, or a jar the user put into the mods folder. */
export type ModSource = 'modrinth' | 'local';

export type ModSort = 'relevance' | 'downloads' | 'updated' | 'newest';

export interface ModSearchQuery {
  query: string;
  /** The profile whose Minecraft version and loader the results must match. */
  profileId: string;
  category: string | null;
  sort: ModSort;
  /** 0-based page. */
  page: number;
  pageSize: number;
}

export interface ModCategory {
  id: string;
  name: string;
}

/** What Medirian needs to install mods into a profile: its Minecraft version and loader. */
export interface ModTargetInfo {
  minecraftVersion: string;
  /** Mod loader of the profile's Medirian target ('fabric' or 'legacy-fabric'). */
  loader: string;
  loaderName: string;
}

/** A Modrinth project. */
export interface ModSummary {
  projectId: string;
  slug: string;
  name: string;
  description: string;
  author: string;
  iconUrl: string | null;
  downloads: number;
  categories: string[];
  /** Minecraft versions the project supports (newest first, as reported by the source). */
  gameVersions: string[];
  loaders: string[];
  pageUrl: string;
  updatedAt: string;
}

export interface ModSearchResult {
  hits: ModSummary[];
  total: number;
  page: number;
  pageSize: number;
  target: ModTargetInfo;
}

export type ModDependencyType = 'required' | 'optional' | 'incompatible' | 'embedded';

export interface ModVersionInfo {
  id: string;
  versionNumber: string;
  name: string;
  gameVersions: string[];
  loaders: string[];
  releaseType: 'release' | 'beta' | 'alpha';
  publishedAt: string;
  fileName: string;
  size: number;
  /** Matches the profile's Minecraft version and loader. */
  compatible: boolean;
  dependencies: { projectId: string; versionId?: string; type: ModDependencyType }[];
}

export interface ModDetails extends ModSummary {
  /** Newest first; the compatible ones are what can be installed into the profile. */
  versions: ModVersionInfo[];
  target: ModTargetInfo;
}

/** One file an install adds to the profile. */
export interface ModInstallStep {
  projectId: string;
  versionId: string;
  name: string;
  versionNumber: string;
  fileName: string;
  size: number;
  /** Why it is installed: the mod itself, or a dependency of it. */
  reason: 'requested' | 'dependency';
}

/**
 * Something that blocks an install or is worth knowing about it. A code with its names, so the
 * launcher shows it in the user's language (renderer i18n "mods.issue.<code>").
 */
export type ModIssue =
  /** No version of the mod runs on the profile's Minecraft version and loader. */
  | { code: 'incompatible'; name: string }
  /** The chosen version does not run on the profile's Minecraft version and loader. */
  | { code: 'versionIncompatible'; name: string; version: string }
  /** A required dependency has no version for the profile. */
  | { code: 'dependencyUnavailable'; name: string; parent: string }
  | { code: 'alreadyInstalled'; name: string; version: string }
  /** A required dependency is installed but disabled; installing enables it. */
  | { code: 'dependencyDisabled'; name: string }
  /** The mod declares itself incompatible with a mod in the profile. */
  | { code: 'conflict'; name: string; other: string };

/** What installing a mod into a profile will do, computed before anything is downloaded. */
export interface ModInstallPlan {
  profileId: string;
  target: ModTargetInfo;
  steps: ModInstallStep[];
  /** Required dependencies already in the profile. */
  satisfied: string[];
  /** Blocking problems (incompatible Minecraft version or loader, unavailable dependency). */
  problems: ModIssue[];
  /** Non-blocking notes (e.g. an installed mod that declares itself incompatible). */
  warnings: ModIssue[];
}

export interface InstalledMod {
  /** File name in the profile's mods folder without ".disabled". */
  file: string;
  enabled: boolean;
  name: string;
  versionNumber: string;
  source: ModSource;
  projectId: string | null;
  versionId: string | null;
  iconUrl: string | null;
  author: string | null;
  pageUrl: string | null;
  size: number;
  /** Required dependencies and whether they are in the profile. */
  dependencies: { name: string; installed: boolean }[];
  /** Installed mods that require this one. */
  requiredBy: string[];
  installedAt: number;
  update: { versionId: string; versionNumber: string } | null;
}

export interface InstalledModsState {
  profileId: string;
  target: ModTargetInfo;
  mods: InstalledMod[];
  /** When updates were last checked (ms), or null. */
  checkedAt: number | null;
}

export interface ModTask {
  profileId: string;
  label: string;
  done: number;
  total: number;
}
