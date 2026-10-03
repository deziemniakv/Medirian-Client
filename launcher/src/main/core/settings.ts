import type { LauncherSettings } from '../../common/types';
import { readJson, writeJson } from './json';

export function defaultSettings(localDistributionDir: string): LauncherSettings {
  return {
    version: 1,
    language: 'en',
    theme: 'auto',
    afterLaunch: 'keep',
    updateChannel: localDistributionDir ? 'local' : 'stable',
    manifestUrl: process.env.MERIDIAN_MANIFEST_URL ?? '',
    localDistributionDir,
    concurrentDownloads: 12,
    reuseMinecraftAssets: true,
    setupCompleted: false,
    selectedProfileId: null,
    msaClientId: ''
  };
}

/** Launcher settings persisted in launcher/settings.json. */
export class SettingsStore {
  private settings: LauncherSettings;

  private constructor(private readonly file: string, settings: LauncherSettings) {
    this.settings = settings;
  }

  static async load(file: string, defaults: LauncherSettings): Promise<SettingsStore> {
    const stored = await readJson<Partial<LauncherSettings>>(file, {});
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
