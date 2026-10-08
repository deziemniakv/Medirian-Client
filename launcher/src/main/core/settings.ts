import type { LauncherSettings } from '../../common/types';
import { readJson, writeJson } from './json';

/** Settings of older launchers that are configured in .env now (docs/OWNER_SETUP.md). */
const RETIRED = ['theme', 'manifestUrl', 'msaClientId', 'servicesUrl', 'discordAppId', 'curseforgeApiKey'];

export function defaultSettings(localDistributionDir: string): LauncherSettings {
  return {
    version: 1,
    language: 'en',
    sceneMotion: true,
    winterSnow: true,
    afterLaunch: 'keep',
    updateChannel: localDistributionDir ? 'local' : 'stable',
    localDistributionDir,
    concurrentDownloads: 12,
    reuseMinecraftAssets: true,
    setupCompleted: false,
    selectedProfileId: null,
    discordPresence: true,
    discordShowServer: true,
    discordShowInLauncher: false
  };
}

/** Launcher settings persisted in launcher/settings.json. */
export class SettingsStore {
  private settings: LauncherSettings;

  private constructor(private readonly file: string, settings: LauncherSettings) {
    this.settings = settings;
  }

  static async load(file: string, defaults: LauncherSettings, packaged: boolean): Promise<SettingsStore> {
    const stored = await readJson<Record<string, unknown>>(file, {});
    for (const key of RETIRED) {
      delete stored[key];
    }
    // installed launchers always use the release channel of their build
    if (packaged) {
      stored.updateChannel = 'stable';
    }
    return new SettingsStore(file, { ...defaults, ...(stored as Partial<LauncherSettings>), version: 1 });
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
