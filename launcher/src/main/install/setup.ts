import { constants } from 'node:fs';
import { access, mkdir, rm, statfs, writeFile } from 'node:fs/promises';
import { arch, freemem, platform, release, totalmem } from 'node:os';
import { join } from 'node:path';
import type { SetupCheck, SystemInfo } from '../../common/types';
import { detectMinecraftDir, type MeridianPaths } from '../core/paths';
import { detectJavaInstallations } from '../java/detect';
import { request } from '../net/http';
import type { UpdateService } from '../updates/updates';

const SUPPORTED = new Set(['win32-x64', 'win32-arm64', 'darwin-x64', 'darwin-arm64', 'linux-x64']);

export async function systemInfo(paths: MeridianPaths): Promise<SystemInfo> {
  let freeDiskMb: number | null = null;
  try {
    const stats = await statfs(paths.root).catch(() => statfs(join(paths.root, '..')));
    freeDiskMb = Math.round((stats.bavail * stats.bsize) / 1024 ** 2);
  } catch {
    freeDiskMb = null;
  }
  return {
    platform: platform(),
    arch: arch(),
    osRelease: release(),
    totalMemoryMb: Math.round(totalmem() / 1024 ** 2),
    freeMemoryMb: Math.round(freemem() / 1024 ** 2),
    freeDiskMb,
    home: paths.root,
    minecraftDir: detectMinecraftDir()
  };
}

/**
 * First-run diagnostics. Every check produces a readable explanation, and fixable problems
 * carry a {@code fix} action the UI offers as a button.
 */
export class SetupService {
  constructor(private readonly paths: MeridianPaths, private readonly updates: UpdateService) {}

  async run(): Promise<SetupCheck[]> {
    const info = await systemInfo(this.paths);
    return Promise.all([
      this.system(info),
      this.memory(info),
      this.disk(info),
      this.network(),
      this.directory(),
      this.java(),
      this.minecraft(info),
      this.config(),
      this.integrity()
    ]);
  }

  async fix(id: SetupCheck['id']): Promise<SetupCheck[]> {
    if (id === 'directory') {
      await mkdir(this.paths.root, { recursive: true });
      for (const dir of [this.paths.launcher, this.paths.config, this.paths.clientProfiles, this.paths.game, this.paths.instances, this.paths.cache]) {
        await mkdir(dir, { recursive: true });
      }
    }
    if (id === 'integrity') {
      await this.updates.refresh(true);
    }
    return this.run();
  }

  private async system(info: SystemInfo): Promise<SetupCheck> {
    const key = `${info.platform}-${info.arch}`;
    return SUPPORTED.has(key)
      ? { id: 'system', status: 'ok', detail: `${info.platform} ${info.arch} (${info.osRelease})` }
      : { id: 'system', status: 'warn', detail: `${key} is not officially supported; Meridian may not start.` };
  }

  private async memory(info: SystemInfo): Promise<SetupCheck> {
    const gb = info.totalMemoryMb / 1024;
    if (gb >= 6) {
      return { id: 'memory', status: 'ok', detail: `${gb.toFixed(1)} GB RAM` };
    }
    if (gb >= 3.5) {
      return { id: 'memory', status: 'warn', detail: `${gb.toFixed(1)} GB RAM — use the Performance profile and 2 GB for the game.` };
    }
    return { id: 'memory', status: 'error', detail: `${gb.toFixed(1)} GB RAM — Minecraft needs at least 4 GB.` };
  }

  private async disk(info: SystemInfo): Promise<SetupCheck> {
    if (info.freeDiskMb === null) {
      return { id: 'disk', status: 'warn', detail: 'Free space could not be determined.' };
    }
    const gb = info.freeDiskMb / 1024;
    if (gb >= 3) {
      return { id: 'disk', status: 'ok', detail: `${gb.toFixed(1)} GB free` };
    }
    return { id: 'disk', status: gb >= 1.5 ? 'warn' : 'error', detail: `${gb.toFixed(1)} GB free — about 1.5 GB is needed for both versions.` };
  }

  private async network(): Promise<SetupCheck> {
    const hosts = ['https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', 'https://meta.fabricmc.net/v2/versions/loader'];
    try {
      await Promise.all(hosts.map((url) => request(url, { timeoutMs: 8000, retries: 0 })));
      return { id: 'network', status: 'ok', detail: 'Mojang and Fabric servers are reachable.' };
    } catch {
      return { id: 'network', status: 'error', detail: 'Cannot reach Mojang/Fabric servers. Check your internet connection or firewall.', fix: 'retry' };
    }
  }

  private async directory(): Promise<SetupCheck> {
    try {
      await access(this.paths.root, constants.W_OK);
      const probe = join(this.paths.root, '.write-test');
      await writeFile(probe, 'ok');
      await rm(probe, { force: true });
      return { id: 'directory', status: 'ok', detail: this.paths.root };
    } catch {
      return { id: 'directory', status: 'error', detail: `${this.paths.root} does not exist or is not writable.`, fix: 'create-directory' };
    }
  }

  private async java(): Promise<SetupCheck> {
    const found = await detectJavaInstallations(this.paths.runtime);
    const summary = found.length > 0 ? `Found ${found.length} Java installation(s). ` : '';
    return {
      id: 'java',
      status: 'ok',
      detail: `${summary}The correct Java for each version (8 for 1.8.9, 21 for 1.21.11, 25 for 26.3) is installed automatically.`
    };
  }

  private async minecraft(info: SystemInfo): Promise<SetupCheck> {
    return info.minecraftDir
      ? { id: 'minecraft', status: 'ok', detail: `Existing installation found (${info.minecraftDir}); its assets will be reused.` }
      : { id: 'minecraft', status: 'ok', detail: 'No existing Minecraft installation — game files will be downloaded on first launch.' };
  }

  private async config(): Promise<SetupCheck> {
    try {
      await mkdir(this.paths.clientProfiles, { recursive: true });
      return { id: 'config', status: 'ok', detail: 'Configuration folders are ready.' };
    } catch {
      return { id: 'config', status: 'error', detail: 'Configuration folder could not be created.', fix: 'create-directory' };
    }
  }

  private async integrity(): Promise<SetupCheck> {
    const state = await this.updates.refresh(false);
    if (!state.manifest) {
      return { id: 'integrity', status: 'warn', detail: state.error ?? 'Release information unavailable.', fix: 'retry' };
    }
    const targets = state.manifest.targets.map((t) => t.displayName).join(', ');
    return { id: 'integrity', status: 'ok', detail: `Meridian ${state.manifest.client.version} available for ${targets}.` };
  }
}
