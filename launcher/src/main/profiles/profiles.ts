import { randomUUID } from 'node:crypto';
import { existsSync, readdirSync } from 'node:fs';
import { cp, mkdir, readdir, rename } from 'node:fs/promises';
import { totalmem } from 'node:os';
import { join } from 'node:path';
import type { LaunchProfile } from '../../common/types';
import { readJson, writeJson } from '../core/json';
import { log } from '../core/log';

/** Sensible default heap: 2–4 GB depending on installed memory. */
export function recommendedMemoryMb(): number {
  const totalGb = totalmem() / 1024 ** 3;
  if (totalGb >= 16) {
    return 4096;
  }
  if (totalGb >= 8) {
    return 3072;
  }
  return 2048;
}

/** A folder name from a profile name: Windows-safe, at most 48 characters. */
export function folderName(name: string): string {
  const cleaned = name
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[. ]+$/, '')
    .slice(0, 48)
    .trim();
  const reserved = /^(con|prn|aux|nul|com\d|lpt\d)$/i;
  return !cleaned || reserved.test(cleaned) ? 'Profile' : cleaned;
}

/** {@code base}, or "base (2)", "base (3)"… whichever is not taken (case-insensitive, like Windows). */
export function uniqueFolder(base: string, taken: Iterable<string>): string {
  const used = new Set([...taken].map((name) => name.toLowerCase()));
  if (!used.has(base.toLowerCase())) {
    return base;
  }
  for (let i = 2; ; i++) {
    const candidate = `${base} (${i})`;
    if (!used.has(candidate.toLowerCase())) {
      return candidate;
    }
  }
}

function profile(name: string, targetId: string, configProfile: string | null, directory: string): LaunchProfile {
  return {
    id: randomUUID(),
    name,
    targetId,
    memoryMb: recommendedMemoryMb(),
    javaPath: null,
    jvmArgs: '',
    resolution: null,
    configProfile,
    createdAt: Date.now(),
    lastPlayed: null,
    directory
  };
}

/** What a duplicated profile takes along: mods, their configuration, options and the mod list. */
const DUPLICATED = ['mods', 'config', 'options.txt', 'optionsof.txt', 'servers.dat', 'medirian-mods.json'];

/**
 * Launch profiles persisted in launcher/profiles.json. Every profile owns a game directory in
 * MEDIRIAN_HOME/profiles/<directory> — its own mods, mod configuration, options and worlds — so
 * profiles never share a mods folder, even when they start the same Minecraft version.
 */
export class ProfileStore {
  private profiles: LaunchProfile[] = [];
  private readonly file: string;
  private readonly root: string;
  private readonly legacyInstances: string;

  // plain fields instead of parameter properties: the tests run with Node's type stripping
  constructor(file: string, root: string, legacyInstances: string) {
    this.file = file;
    this.root = root;
    this.legacyInstances = legacyInstances;
  }

  async load(): Promise<void> {
    const stored = await readJson<{ profiles?: LaunchProfile[] }>(this.file, {});
    this.profiles = stored.profiles ?? [];
    if (this.profiles.length === 0) {
      // first run: one profile per supported branch
      this.profiles = [];
      for (const [name, target, config] of [['PvP 1.8.9', '1.8.9', 'PvP'], ['Survival 1.21.11', '1.21.11', 'Default'],
        ['Latest 26.3', '26.3', 'Default']] as const) {
        this.profiles.push(profile(name, target, config, this.allocate(name)));
      }
      await this.persist();
    }
    if (this.profiles.some((p) => !p.directory)) {
      await this.migrate();
    }
  }

  /**
   * Profiles from before 0.2.0 shared one game folder per Minecraft version (instances/<target>).
   * Every profile now gets its own folder; the old folder (worlds, options, screenshots) moves to
   * the first profile of that version, the others start fresh.
   */
  private async migrate(): Promise<void> {
    const claimed = new Set<string>();
    for (const p of this.profiles) {
      if (p.directory) {
        continue;
      }
      p.directory = this.allocate(p.name);
      const legacy = join(this.legacyInstances, p.targetId);
      const destination = join(this.root, p.directory);
      if (!claimed.has(p.targetId) && existsSync(legacy) && !existsSync(destination)) {
        claimed.add(p.targetId);
        try {
          await mkdir(this.root, { recursive: true });
          await rename(legacy, destination);
          log.info(`Moved the ${p.targetId} game folder to profile "${p.name}" (${destination})`);
        } catch (error) {
          log.warn(`Could not move ${legacy} to ${destination}`, error);
        }
      }
    }
    await this.persist();
  }

  /** A free folder for a new profile called {@code name}. */
  private allocate(name: string): string {
    const taken = this.profiles.map((p) => p.directory).filter(Boolean);
    let existing: string[] = [];
    try {
      existing = existsSync(this.root) ? readdirSync(this.root) : [];
    } catch {
      existing = [];
    }
    return uniqueFolder(folderName(name), [...taken, ...existing]);
  }

  list(): LaunchProfile[] {
    return this.profiles;
  }

  get(id: string): LaunchProfile | undefined {
    return this.profiles.find((p) => p.id === id);
  }

  /** The game directory of a profile. */
  directoryOf(p: LaunchProfile): string {
    return join(this.root, p.directory);
  }

  async create(base: Partial<LaunchProfile>): Promise<LaunchProfile> {
    const name = base.name?.trim() || 'New profile';
    const created: LaunchProfile = {
      ...profile(name, base.targetId ?? '1.8.9', base.configProfile ?? null, ''),
      ...base,
      name,
      id: randomUUID(),
      createdAt: Date.now(),
      lastPlayed: null,
      // a new profile always gets a new folder, also when created from another profile
      directory: this.allocate(name)
    };
    this.profiles.push(validate(created));
    await mkdir(this.directoryOf(created), { recursive: true });
    await this.persist();
    return created;
  }

  /** A copy of a profile with a copy of its mods, mod configuration and options (not its worlds). */
  async duplicate(id: string, name: string): Promise<LaunchProfile> {
    const source = this.get(id);
    if (!source) {
      throw new Error('Profile not found');
    }
    const { id: _id, directory: _directory, createdAt: _createdAt, lastPlayed: _lastPlayed, ...settings } = source;
    const copy = await this.create({ ...settings, name });
    const from = this.directoryOf(source);
    const to = this.directoryOf(copy);
    for (const entry of DUPLICATED) {
      const path = join(from, entry);
      if (existsSync(path)) {
        await cp(path, join(to, entry), { recursive: true, force: true });
      }
    }
    return copy;
  }

  async save(updated: LaunchProfile): Promise<LaunchProfile[]> {
    const index = this.profiles.findIndex((p) => p.id === updated.id);
    if (index < 0) {
      throw new Error('Profile not found');
    }
    // the folder is chosen once and never moves (a renamed profile keeps its worlds and mods)
    this.profiles[index] = validate({ ...updated, directory: this.profiles[index].directory });
    await this.persist();
    return this.profiles;
  }

  /** Removes the profile; its folder stays on disk (worlds are precious). */
  async remove(id: string): Promise<LaunchProfile[]> {
    if (this.profiles.length <= 1) {
      throw new Error('At least one profile is required');
    }
    this.profiles = this.profiles.filter((p) => p.id !== id);
    await this.persist();
    return this.profiles;
  }

  async markPlayed(id: string): Promise<void> {
    const found = this.get(id);
    if (found) {
      found.lastPlayed = Date.now();
      await this.persist();
    }
  }

  private persist(): Promise<void> {
    return writeJson(this.file, { version: 1, profiles: this.profiles });
  }
}

function validate(p: LaunchProfile): LaunchProfile {
  return {
    ...p,
    name: p.name.trim().slice(0, 40) || 'Profile',
    memoryMb: Math.max(1024, Math.min(32768, Math.round(p.memoryMb / 256) * 256)),
    jvmArgs: p.jvmArgs.trim(),
    resolution: p.resolution && p.resolution.width >= 320 && p.resolution.height >= 240 ? p.resolution : null
  };
}

/** Names of Medirian client configuration profiles (config/profiles/*.json). */
export async function listClientConfigProfiles(directory: string): Promise<string[]> {
  try {
    const files = (await readdir(directory)).filter((name) => name.endsWith('.json'));
    const names = await Promise.all(
      files.map(async (file) => (await readJson<{ name?: string }>(`${directory}/${file}`, {})).name ?? file.replace(/\.json$/, ''))
    );
    return names.sort((a, b) => a.localeCompare(b));
  } catch {
    return ['Default', 'PvP', 'Performance'];
  }
}
