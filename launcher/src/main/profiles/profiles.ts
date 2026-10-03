import { randomUUID } from 'node:crypto';
import { readdir } from 'node:fs/promises';
import { totalmem } from 'node:os';
import type { LaunchProfile } from '../../common/types';
import { readJson, writeJson } from '../core/json';

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

function profile(name: string, targetId: string, configProfile: string | null): LaunchProfile {
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
    lastPlayed: null
  };
}

/** Launch profiles persisted in launcher/profiles.json. */
export class ProfileStore {
  private profiles: LaunchProfile[] = [];

  constructor(private readonly file: string) {}

  async load(): Promise<void> {
    const stored = await readJson<{ profiles?: LaunchProfile[] }>(this.file, {});
    this.profiles = stored.profiles ?? [];
    if (this.profiles.length === 0) {
      // first run: one profile per supported branch
      this.profiles = [profile('PvP 1.8.9', '1.8.9', 'PvP'), profile('Survival 1.21.11', '1.21.11', 'Default')];
      await this.persist();
    }
  }

  list(): LaunchProfile[] {
    return this.profiles;
  }

  get(id: string): LaunchProfile | undefined {
    return this.profiles.find((p) => p.id === id);
  }

  async create(base: Partial<LaunchProfile>): Promise<LaunchProfile> {
    const created: LaunchProfile = {
      ...profile(base.name?.trim() || 'New profile', base.targetId ?? '1.8.9', base.configProfile ?? null),
      ...base,
      id: randomUUID(),
      createdAt: Date.now(),
      lastPlayed: null
    };
    this.profiles.push(validate(created));
    await this.persist();
    return created;
  }

  async save(updated: LaunchProfile): Promise<LaunchProfile[]> {
    const index = this.profiles.findIndex((p) => p.id === updated.id);
    if (index < 0) {
      throw new Error('Profile not found');
    }
    this.profiles[index] = validate(updated);
    await this.persist();
    return this.profiles;
  }

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

/** Names of Meridian client configuration profiles (config/profiles/*.json). */
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
