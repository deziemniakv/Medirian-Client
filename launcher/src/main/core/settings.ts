import type { LauncherSettings } from '../../common/types';
import { readJson, writeJson } from './json';

/**
 * Release manifest of the stable channel when the user has not set one: MEDIRIAN_MANIFEST_URL at
 * runtime, else the URL built in by the release workflow (MAIN_VITE_MANIFEST_URL).
 */
export function defaultManifestUrl(): string {
  return process.env.MEDIRIAN_MANIFEST_URL || import.meta.env.MAIN_VITE_MANIFEST_URL || '';
}

/** Medirian services when the user has not set a URL: MEDIRIAN_SERVICES_URL, else the built-in one. */
export function defaultServicesUrl(): string {
  return process.env.MEDIRIAN_SERVICES_URL || import.meta.env.MAIN_VITE_SERVICES_URL || '';
}

export function defaultSettings(localDistributionDir: string): LauncherSettings {
  return {
    version: 1,
    language: 'en',
    sceneMotion: true,
    winterSnow: true,
    afterLaunch: 'keep',
    updateChannel: localDistributionDir ? 'local' : 'stable',
    manifestUrl: '',
    localDistributionDir,
    concurrentDownloads: 12,
    reuseMinecraftAssets: true,
    setupCompleted: false,
    selectedProfileId: null,
    msaClientId: '',
    servicesUrl: '',
    discordPresence: true,
    discordShowServer: true,
    discordShowInLauncher: false,
    discordAppId: ''
  };
}

/** Launcher settings persisted in launcher/settings.json. */
export class SettingsStore {
  private settings: LauncherSettings;

  private constructor(private readonly file: string, settings: LauncherSettings) {
    this.settings = settings;
  }

  static async load(file: string, defaults: LauncherSettings): Promise<SettingsStore> {
    const stored = await readJson<Partial<LauncherSettings> & { theme?: unknown }>(file, {});
    // seasonal themes are gone: Halloween is Medirian's look all year
    delete stored.theme;
    return new SettingsStore(file, { ...defaults, ...stored, version: 1 });
  }

  get(): LauncherSettings {
    return this.settings;
  }

  async update(patch: Partial<LauncherSettings>): Promise<LauncherSettings> {
    const next = { ...this.settings, ...patch, version: 1 as const };
    next.concurrentDownloads = Math.max(1, Math.min(32, Math.round(next.concurrentDownloads)));
    this.settings = next;
    await writeJson(this.file, next);
    return next;
  }
}
