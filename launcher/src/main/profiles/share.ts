import { existsSync } from 'node:fs';
import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { LaunchProfile, ShareError, ShareExport, ShareImportMode, SharePreview, ShareResult } from '../../common/types';
import { readJson, writeJson } from '../core/json';
import { HttpError, request } from '../net/http';

/** What a profile code carries (also the backend's "medirian-profile" format). */
export interface SharedProfile {
  format: 'medirian-profile';
  version: 1;
  /** Launch profile name. */
  name: string;
  /** Safe launch settings: never the Java path or JVM arguments (they could run anything). */
  launch: { targetId: string; memoryMb: number; resolution: { width: number; height: number } | null };
  /** The Medirian configuration profile: modules, their settings, keybinds, HUD layout and look. */
  profile: Record<string, unknown> | null;
  /** Medirian client settings without personal ones (language). */
  client: Record<string, unknown> | null;
  /** Minecraft video options from options.txt (only the keys in GAME_OPTIONS). */
  game: Record<string, string>;
}

/** Video / visual options that are safe and meaningful to share (options.txt keys of all versions). */
export const GAME_OPTIONS = new Set([
  'renderDistance', 'simulationDistance', 'entityDistanceScaling', 'particles', 'renderClouds', 'cloudStatus',
  'graphicsMode', 'fancyGraphics', 'ao', 'entityShadows', 'enableVsync', 'maxFps', 'guiScale', 'mipmapLevels',
  'biomeBlendRadius', 'gamma', 'fov', 'fovEffectScale', 'screenEffectScale', 'bobView', 'useVbo', 'anaglyph3d',
  'chatOpacity', 'chatScale', 'chatWidth', 'textBackgroundOpacity', 'menuBackgroundBlurriness', 'prioritizeChunkUpdates',
  'damageTiltStrength', 'darknessEffectScale', 'glintSpeed', 'glintStrength', 'showAutosaveIndicator', 'attackIndicator',
  'cutoutLeaves', 'improvedTransparency', 'weatherRadius', 'vignette', 'chunkSectionFadeInTime', 'textureFiltering',
  'maxAnisotropyBit', 'inactivityFpsLimit'
]);

/** Client settings that are about taste, not about the person (no language). */
const CLIENT_SETTINGS = new Set(['mainMenu', 'winterSnow', 'font', 'notifications', 'animations', 'hideHudInDebug', 'modMenuKey', 'hudEditorKey', 'emoteKey']);

interface StoredShare {
  code: string;
  profileId: string;
  name: string;
  expiresAt: number;
  deleteKey: string;
}

/** The file name of a Medirian configuration profile (ConfigManager.slug in the client). */
export function configSlug(name: string): string {
  const slug = name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-+|-+$)/g, '');
  return slug || 'profile';
}

/** A configuration profile name the client accepts: ≤ 24 letters, digits, spaces, - and _. */
export function configName(name: string): string {
  const clean = name.replace(/[^A-Za-z0-9 _-]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 24).trim();
  return clean || 'Imported';
}

/** MDN-XXXX-XXXX-XXXX from what someone typed or pasted, or null. */
export function normalizeCode(value: string): string | null {
  const raw = value.toUpperCase().replace(/[\s-]/g, '').replace(/^MDN/, '');
  if (!/^[2-9A-HJKMNP-Z]{12}$/.test(raw)) {
    return null;
  }
  return `MDN-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8)}`;
}

export function parseOptions(text: string): Map<string, string> {
  const options = new Map<string, string>();
  for (const line of text.split(/\r?\n/)) {
    const at = line.indexOf(':');
    if (at > 0) {
      options.set(line.slice(0, at), line.slice(at + 1));
    }
  }
  return options;
}

export interface ShareDeps {
  servicesUrl: string;
  configDir: string;
  clientFile: string;
  sharesFile: string;
  profiles: {
    list(): LaunchProfile[];
    get(id: string): LaunchProfile | undefined;
    directoryOf(p: LaunchProfile): string;
    create(base: Partial<LaunchProfile>): Promise<LaunchProfile>;
    save(p: LaunchProfile): Promise<LaunchProfile[]>;
  };
  isRunning(profileId: string): boolean;
}

const fail = (error: ShareError): { ok: false; error: ShareError } => ({ ok: false, error });

function errorOf(error: unknown): ShareError {
  if (error instanceof HttpError) {
    return error.status === 400 ? 'invalid' : error.status === 404 ? 'notFound' : error.status === 410 ? 'expired'
      : error.status === 429 ? 'tooMany' : error.status === 403 ? 'forbidden' : 'offline';
  }
  return 'offline';
}

/**
 * Profile codes (MDN-XXXX-XXXX-XXXX): a launch profile with its Medirian settings, modules, HUD and
 * video options, stored by Medirian Services for 90 days. Nothing private goes into a code
 * (accounts, tokens, Java path, JVM arguments, language); the services strip credential-like keys
 * again on their side.
 */
export class ProfileShareService {
  private readonly deps: ShareDeps;
  /** Codes looked up for import, so applying does not fetch again. */
  private readonly fetched = new Map<string, SharedProfile>();

  constructor(deps: ShareDeps) {
    this.deps = deps;
  }

  private api(path: string): string {
    return `${this.deps.servicesUrl}${path}`;
  }

  /** The configuration profile file of a name, found by its "name" field or its slug. */
  private async configFile(name: string): Promise<string | null> {
    const dir = this.deps.configDir;
    const bySlug = join(dir, `${configSlug(name)}.json`);
    if (existsSync(bySlug)) {
      return bySlug;
    }
    for (const file of await readdir(dir).catch(() => [] as string[])) {
      if (file.endsWith('.json')) {
        const json = await readJson<{ name?: string }>(join(dir, file), {});
        if (json.name?.toLowerCase() === name.toLowerCase()) {
          return join(dir, file);
        }
      }
    }
    return null;
  }

  /** The profile as it would be shared. */
  async payload(profile: LaunchProfile): Promise<SharedProfile | null> {
    const client = await readJson<{ activeProfile?: string; settings?: Record<string, unknown> }>(this.deps.clientFile, {});
    const configName = profile.configProfile ?? client.activeProfile ?? 'Default';
    const file = await this.configFile(configName);
    const config = file ? await readJson<Record<string, unknown> | null>(file, null) : null;
    if (!config) {
      return null;
    }
    const options = parseOptions(await readFile(join(this.deps.profiles.directoryOf(profile), 'options.txt'), 'utf8').catch(() => ''));
    const game: Record<string, string> = {};
    for (const [key, value] of options) {
      if (GAME_OPTIONS.has(key)) {
        game[key] = value;
      }
    }
    const clientSettings = Object.fromEntries(Object.entries(client.settings ?? {}).filter(([key]) => CLIENT_SETTINGS.has(key)));
    return {
      format: 'medirian-profile',
      version: 1,
      name: profile.name,
      launch: { targetId: profile.targetId, memoryMb: profile.memoryMb, resolution: profile.resolution },
      profile: config,
      client: clientSettings,
      game
    };
  }

  async export(profileId: string): Promise<ShareResult<ShareExport>> {
    if (!this.deps.servicesUrl) {
      return fail('unconfigured');
    }
    const profile = this.deps.profiles.get(profileId);
    if (!profile) {
      return fail('notFound');
    }
    const payload = await this.payload(profile);
    if (!payload) {
      return fail('noSettings');
    }
    try {
      const response = await request(this.api('/v1/shares'), {
        retries: 1,
        init: { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profile: payload }) }
      });
      const created = (await response.json()) as { code: string; expiresAt: number; deleteKey: string };
      const shares = await readJson<StoredShare[]>(this.deps.sharesFile, []);
      shares.push({ code: created.code, profileId, name: profile.name, expiresAt: created.expiresAt, deleteKey: created.deleteKey });
      await writeJson(this.deps.sharesFile, shares.filter((s) => s.expiresAt > Date.now()).slice(-100));
      return { ok: true, value: { code: created.code, expiresAt: created.expiresAt, name: profile.name } };
    } catch (error) {
      return fail(errorOf(error));
    }
  }

  /** Withdraws a code this launcher created. */
  async delete(code: string): Promise<ShareResult<null>> {
    if (!this.deps.servicesUrl) {
      return fail('unconfigured');
    }
    const shares = await readJson<StoredShare[]>(this.deps.sharesFile, []);
    const share = shares.find((s) => s.code === code);
    if (!share) {
      return fail('forbidden');
    }
    try {
      await request(this.api(`/v1/shares/${code}`), {
        retries: 1,
        init: { method: 'DELETE', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ deleteKey: share.deleteKey }) }
      });
    } catch (error) {
      if (!(error instanceof HttpError && error.status === 404)) {
        return fail(errorOf(error));
      }
    }
    await writeJson(this.deps.sharesFile, shares.filter((s) => s.code !== code));
    return { ok: true, value: null };
  }

  /** Looks a code up and describes what importing it would do. */
  async preview(input: string): Promise<ShareResult<SharePreview>> {
    if (!this.deps.servicesUrl) {
      return fail('unconfigured');
    }
    const code = normalizeCode(input);
    if (!code) {
      return fail('invalid');
    }
    let shared: SharedProfile;
    let expiresAt: number;
    try {
      const response = await request(this.api(`/v1/shares/${code}`), { retries: 1 });
      const body = (await response.json()) as { profile: SharedProfile; expiresAt: number };
      shared = body.profile;
      expiresAt = body.expiresAt;
    } catch (error) {
      return fail(errorOf(error));
    }
    if (shared?.format !== 'medirian-profile' || typeof shared.name !== 'string' || !shared.launch) {
      return fail('invalid');
    }
    this.fetched.set(code, shared);
    const modules = (shared.profile?.modules ?? {}) as Record<string, { enabled?: boolean; hud?: unknown }>;
    const existing = this.deps.profiles.list().find((p) => p.name.toLowerCase() === shared.name.toLowerCase()) ?? null;
    return {
      ok: true,
      value: {
        code,
        name: shared.name,
        targetId: shared.launch.targetId,
        memoryMb: shared.launch.memoryMb,
        modules: Object.keys(modules).length,
        enabledModules: Object.values(modules).filter((m) => m?.enabled).length,
        gameOptions: Object.keys(shared.game ?? {}).length,
        hasClientSettings: Object.keys(shared.client ?? {}).length > 0,
        expiresAt,
        existingProfileId: existing?.id ?? null
      }
    };
  }

  /**
   * Imports a looked-up code: as a new launch profile, or over an existing one (its Medirian
   * settings, video options and launch settings are replaced). Client settings only when asked.
   */
  async apply(code: string, mode: ShareImportMode, targetProfileId: string | null, withClientSettings: boolean): Promise<ShareResult<LaunchProfile>> {
    const shared = this.fetched.get(code);
    if (!shared) {
      return fail('notFound');
    }
    if (mode === 'replace' && targetProfileId && this.deps.isRunning(targetProfileId)) {
      return fail('running');
    }
    let profile: LaunchProfile | undefined;
    const launch = {
      targetId: shared.launch.targetId,
      memoryMb: Math.max(1024, Math.min(32768, Number(shared.launch.memoryMb) || 2048)),
      resolution: shared.launch.resolution && shared.launch.resolution.width >= 320 ? shared.launch.resolution : null
    };
    // the configuration profile: a new name for a new profile, the replaced profile's own otherwise
    const taken = new Set((await readdir(this.deps.configDir).catch(() => [] as string[])).map((f) => f.replace(/\.json$/, '')));
    let config = configName(shared.name);
    if (mode === 'replace' && targetProfileId) {
      profile = this.deps.profiles.get(targetProfileId);
      if (!profile) {
        return fail('notFound');
      }
      config = profile.configProfile && !['default', 'pvp', 'performance'].includes(configSlug(profile.configProfile))
        ? profile.configProfile : configName(profile.name);
    } else {
      for (let i = 2; taken.has(configSlug(config)); i++) {
        config = configName(`${configName(shared.name).slice(0, 20)} ${i}`);
      }
    }
    if (shared.profile) {
      await mkdir(this.deps.configDir, { recursive: true });
      await writeJson(join(this.deps.configDir, `${configSlug(config)}.json`), { ...shared.profile, name: config });
    }
    if (profile) {
      await this.deps.profiles.save({ ...profile, ...launch, configProfile: shared.profile ? config : profile.configProfile });
      profile = this.deps.profiles.get(profile.id)!;
    } else {
      const names = new Set(this.deps.profiles.list().map((p) => p.name.toLowerCase()));
      let name = shared.name.slice(0, 40);
      for (let i = 2; names.has(name.toLowerCase()); i++) {
        name = `${shared.name.slice(0, 34)} (${i})`;
      }
      profile = await this.deps.profiles.create({ name, ...launch, configProfile: shared.profile ? config : null });
    }
    await this.writeOptions(profile, shared.game ?? {});
    if (withClientSettings && shared.client) {
      const client = await readJson<{ settings?: Record<string, unknown> }>(this.deps.clientFile, {});
      const allowed = Object.fromEntries(Object.entries(shared.client).filter(([key]) => CLIENT_SETTINGS.has(key)));
      await writeJson(this.deps.clientFile, { ...client, settings: { ...(client.settings ?? {}), ...allowed } });
    }
    this.fetched.delete(code);
    return { ok: true, value: profile };
  }

  /** Merges shared video options into the profile's options.txt (other lines stay). */
  private async writeOptions(profile: LaunchProfile, game: Record<string, string>): Promise<void> {
    const entries = Object.entries(game).filter(([key, value]) => GAME_OPTIONS.has(key) && typeof value === 'string' && !/[\r\n]/.test(value));
    if (entries.length === 0) {
      return;
    }
    const dir = this.deps.profiles.directoryOf(profile);
    await mkdir(dir, { recursive: true });
    const file = join(dir, 'options.txt');
    const lines = (await readFile(file, 'utf8').catch(() => '')).split(/\r?\n/).filter(Boolean);
    const options = new Map(lines.filter((line) => line.indexOf(':') > 0).map((line) => [line.slice(0, line.indexOf(':')), line] as const));
    for (const [key, value] of entries) {
      options.set(key, `${key}:${value}`);
    }
    await writeFile(file, [...options.values()].join('\n') + '\n');
  }
}
