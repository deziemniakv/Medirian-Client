import { existsSync } from 'node:fs';
import { readdir, rm } from 'node:fs/promises';
import { dirname, isAbsolute, join } from 'node:path';
import type { LauncherSettings, ReleaseManifest, ReleaseState, ReleaseTarget, TargetStatus } from '../../common/types';
import { readJson, writeJson } from '../core/json';
import { defaultManifestUrl } from '../core/settings';
import { log } from '../core/log';
import type { MedirianPaths } from '../core/paths';
import { downloadAll, isValid, type DownloadProgress } from '../net/downloader';
import { fetchJson } from '../net/http';

interface InstalledClient {
  version: string;
  sha1: string;
  file: string;
}

const REFRESH_INTERVAL_MS = 5 * 60 * 1000;

/** Compares dotted versions numerically ("0.10.0" > "0.9.3"). */
export function compareVersions(a: string, b: string): number {
  const pa = a.split(/[.+-]/).map((x) => parseInt(x, 10) || 0);
  const pb = b.split(/[.+-]/).map((x) => parseInt(x, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) {
      return d;
    }
  }
  return 0;
}

/**
 * Release manifest handling. Channels:
 *  - stable: manifest downloaded from the configured URL
 *  - local:  manifest + jars produced by scripts/build-clients.mjs in a local directory
 * The last good manifest is cached so the launcher works offline.
 */
export class UpdateService {
  private state: ReleaseState = { manifest: null, error: null, source: '', checkedAt: 0 };

  constructor(private readonly paths: MedirianPaths, private readonly settings: () => LauncherSettings) {}

  private cacheFile(): string {
    return join(this.paths.cache, 'release-manifest.json');
  }

  private source(): { kind: 'url' | 'file'; location: string } {
    const s = this.settings();
    if (s.updateChannel === 'local') {
      return { kind: 'file', location: join(s.localDistributionDir, 'release-manifest.json') };
    }
    return { kind: 'url', location: s.manifestUrl || defaultManifestUrl() };
  }

  async refresh(force = false): Promise<ReleaseState> {
    const src = this.source();
    if (!force && this.state.manifest && this.state.source === src.location && Date.now() - this.state.checkedAt < REFRESH_INTERVAL_MS) {
      return this.state;
    }
    try {
      if (!src.location) {
        throw new Error('No update server configured. Set the manifest URL in Settings → Updates.');
      }
      const manifest = src.kind === 'file'
        ? await readJson<ReleaseManifest | null>(src.location, null)
        : await fetchJson<ReleaseManifest>(src.location);
      if (!manifest || manifest.schema !== 1 || !Array.isArray(manifest.targets)) {
        throw new Error(src.kind === 'file' ? `No release manifest found at ${src.location}` : 'The update server returned an invalid manifest.');
      }
      await writeJson(this.cacheFile(), { source: src.location, manifest });
      this.state = { manifest, error: null, source: src.location, checkedAt: Date.now() };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const cached = await readJson<{ source: string; manifest: ReleaseManifest } | null>(this.cacheFile(), null);
      if (cached?.source === src.location) {
        log.warn(`Using cached release manifest: ${message}`);
        this.state = { manifest: cached.manifest, error: message, source: src.location, checkedAt: Date.now() };
      } else {
        this.state = { manifest: null, error: message, source: src.location, checkedAt: Date.now() };
      }
    }
    return this.state;
  }

  current(): ReleaseState {
    return this.state;
  }

  target(id: string): ReleaseTarget | undefined {
    return this.state.manifest?.targets.find((t) => t.id === id);
  }

  private installedFile(targetId: string): string {
    return join(this.paths.clients, targetId, 'installed.json');
  }

  async installed(targetId: string): Promise<InstalledClient | null> {
    const info = await readJson<InstalledClient | null>(this.installedFile(targetId), null);
    if (info && existsSync(info.file)) {
      return info;
    }
    return null;
  }

  async statuses(versionsDir: string): Promise<TargetStatus[]> {
    const manifest = this.state.manifest;
    if (!manifest) {
      return [];
    }
    return Promise.all(manifest.targets.map(async (target) => {
      const installed = await this.installed(target.id);
      return {
        targetId: target.id,
        installedVersion: installed?.version ?? null,
        latestVersion: manifest.client.version,
        updateAvailable: !installed || installed.sha1 !== target.artifact.sha1,
        minecraftInstalled: existsSync(join(versionsDir, target.minecraftVersion, `${target.minecraftVersion}.jar`))
      };
    }));
  }

  /** Ensures the Medirian jar for {@code target} is installed and verified; returns its path. */
  async ensureClient(target: ReleaseTarget, onProgress?: (p: DownloadProgress) => void): Promise<string> {
    const manifest = this.state.manifest!;
    const version = manifest.client.version;
    const installed = await this.installed(target.id);
    if (installed && installed.sha1 === target.artifact.sha1 && (await isValid(installed.file, installed.sha1, target.artifact.size, 'quick'))) {
      return installed.file;
    }
    const fileName = target.artifact.file ?? target.artifact.url?.split('/').pop() ?? `medirian-${target.id}.jar`;
    const destination = join(this.paths.clients, target.id, version, fileName.replace(/[\\/]/g, '_'));
    const src = this.source();
    const localSource = target.artifact.file
      ? isAbsolute(target.artifact.file) ? target.artifact.file : join(dirname(src.location), target.artifact.file)
      : undefined;
    await downloadAll([{
      url: target.artifact.url ?? `file-unavailable:${fileName}`,
      path: destination,
      sha1: target.artifact.sha1,
      size: target.artifact.size,
      localSource
    }], { concurrency: 1, verify: 'full', onProgress });
    await writeJson(this.installedFile(target.id), { version, sha1: target.artifact.sha1, file: destination });
    await this.removeOldVersions(target.id, version);
    log.info(`Installed Medirian ${version} for ${target.id}`);
    return destination;
  }

  private async removeOldVersions(targetId: string, keep: string): Promise<void> {
    const dir = join(this.paths.clients, targetId);
    for (const entry of await readdir(dir, { withFileTypes: true }).catch(() => [])) {
      if (entry.isDirectory() && entry.name !== keep) {
        await rm(join(dir, entry.name), { recursive: true, force: true });
      }
    }
  }
}
