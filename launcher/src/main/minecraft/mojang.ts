import { existsSync } from 'node:fs';
import { stat } from 'node:fs/promises';
import { join } from 'node:path';
import { readJson, writeJson } from '../core/json';
import { log } from '../core/log';
import type { MeridianPaths } from '../core/paths';
import { downloadAll, type DownloadJob } from '../net/downloader';
import { fetchJson } from '../net/http';
import { mavenPath } from './maven';
import { archBits, osName, rulesAllow, type Rule } from './rules';

export const VERSION_MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
export const RESOURCES_URL = 'https://resources.download.minecraft.net';
export const MOJANG_LIBRARIES_URL = 'https://libraries.minecraft.net/';

export interface Artifact {
  path?: string;
  url: string;
  sha1: string;
  size: number;
}

export interface Library {
  name: string;
  downloads?: { artifact?: Artifact; classifiers?: Record<string, Artifact> };
  natives?: Record<string, string>;
  extract?: { exclude?: string[] };
  rules?: Rule[];
  /** Maven repository base (loader profiles). */
  url?: string;
  sha1?: string;
  size?: number;
}

export type Argument = string | { rules?: Rule[]; value: string | string[] };

export interface VersionJson {
  id: string;
  type: string;
  mainClass: string;
  inheritsFrom?: string;
  minecraftArguments?: string;
  arguments?: { game?: Argument[]; jvm?: Argument[] };
  assetIndex: { id: string; url: string; sha1: string; size: number; totalSize?: number };
  assets: string;
  downloads: { client: Artifact };
  libraries: Library[];
  javaVersion?: { component: string; majorVersion: number };
}

interface ManifestEntry {
  id: string;
  type: string;
  url: string;
  sha1: string;
}

interface VersionManifest {
  latest: { release: string; snapshot: string };
  versions: ManifestEntry[];
}

interface AssetIndex {
  objects: Record<string, { hash: string; size: number }>;
  virtual?: boolean;
  map_to_resources?: boolean;
}

export interface ResolvedLibraries {
  /** Absolute paths of classpath jars, in order. */
  classpath: string[];
  jobs: DownloadJob[];
  /** Native archives to extract (legacy LWJGL 2). */
  natives: { path: string; exclude: string[] }[];
}

const MANIFEST_TTL_MS = 60 * 60 * 1000;

/** Access to Mojang's version metadata, client jars, libraries and assets. */
export class MojangService {
  constructor(private readonly paths: MeridianPaths) {}

  /** Version manifest, cached for an hour; falls back to the cache when offline. */
  async versionManifest(): Promise<VersionManifest> {
    const cacheFile = join(this.paths.cache, 'version_manifest_v2.json');
    try {
      const info = await stat(cacheFile);
      if (Date.now() - info.mtimeMs < MANIFEST_TTL_MS) {
        const cached = await readJson<VersionManifest | null>(cacheFile, null);
        if (cached?.versions) {
          return cached;
        }
      }
    } catch {
      // no cache yet
    }
    try {
      return await this.downloadManifest(cacheFile);
    } catch (error) {
      if (existsSync(cacheFile)) {
        log.warn('Version manifest unavailable, using cached copy', error);
        return readJson<VersionManifest>(cacheFile, { latest: { release: '', snapshot: '' }, versions: [] });
      }
      throw error;
    }
  }

  private async downloadManifest(cacheFile: string): Promise<VersionManifest> {
    const manifest = await fetchJson<VersionManifest>(VERSION_MANIFEST_URL);
    await writeJson(cacheFile, manifest);
    return manifest;
  }

  versionDir(id: string): string {
    return join(this.paths.versions, id);
  }

  /** Version JSON for a vanilla version (downloaded and verified on first use). */
  async versionJson(id: string): Promise<VersionJson> {
    const file = join(this.versionDir(id), `${id}.json`);
    if (existsSync(file)) {
      const cached = await readJson<VersionJson | null>(file, null);
      if (cached && cached.id === id) {
        return cached;
      }
    }
    const manifest = await this.versionManifest();
    const entry = manifest.versions.find((version) => version.id === id);
    if (!entry) {
      throw new Error(`Minecraft ${id} is not in Mojang's version manifest`);
    }
    await downloadAll([{ url: entry.url, path: file, sha1: entry.sha1 }], { concurrency: 1, verify: 'full' });
    return readJson<VersionJson>(file, null as never);
  }

  clientJar(version: VersionJson): DownloadJob {
    const client = version.downloads.client;
    return { url: client.url, path: join(this.versionDir(version.id), `${version.id}.jar`), sha1: client.sha1, size: client.size };
  }

  /** Resolves libraries allowed on this OS into classpath entries, download jobs and natives. */
  resolveLibraries(libraries: Library[]): ResolvedLibraries {
    const result: ResolvedLibraries = { classpath: [], jobs: [], natives: [] };
    const os = osName();
    for (const library of libraries) {
      if (!rulesAllow(library.rules)) {
        continue;
      }
      if (library.natives) {
        const classifierTemplate = library.natives[os];
        const classifier = classifierTemplate?.replace('${arch}', archBits());
        const artifact = classifier ? library.downloads?.classifiers?.[classifier] : undefined;
        if (artifact) {
          const path = join(this.paths.libraries, artifact.path ?? mavenPath(`${library.name}:${classifier}`));
          result.jobs.push({ url: artifact.url, path, sha1: artifact.sha1, size: artifact.size });
          result.natives.push({ path, exclude: library.extract?.exclude ?? ['META-INF/'] });
        }
        // legacy native-only entries have no main artifact
        if (!library.downloads?.artifact) {
          continue;
        }
      }
      const artifact = library.downloads?.artifact;
      if (artifact) {
        const path = join(this.paths.libraries, artifact.path ?? mavenPath(library.name));
        result.classpath.push(path);
        if (artifact.url) {
          result.jobs.push({ url: artifact.url, path, sha1: artifact.sha1, size: artifact.size });
        }
        continue;
      }
      // Maven-style library (loader profiles)
      const relative = mavenPath(library.name);
      const path = join(this.paths.libraries, relative);
      const base = library.url ?? MOJANG_LIBRARIES_URL;
      result.classpath.push(path);
      result.jobs.push({ url: base.replace(/\/?$/, '/') + relative, path, sha1: library.sha1, size: library.size });
    }
    return result;
  }

  /**
   * Asset download jobs for a version. When an existing .minecraft directory is given, its
   * objects are copied instead of downloaded (they are content addressed, so this is safe).
   */
  async assetJobs(version: VersionJson, reuseFrom: string | null): Promise<{ indexId: string; jobs: DownloadJob[]; index: AssetIndex }> {
    const indexInfo = version.assetIndex;
    const indexFile = join(this.paths.assets, 'indexes', `${indexInfo.id}.json`);
    await downloadAll([{ url: indexInfo.url, path: indexFile, sha1: indexInfo.sha1, size: indexInfo.size }], {
      concurrency: 1,
      verify: 'quick'
    });
    const index = await readJson<AssetIndex>(indexFile, { objects: {} });
    const jobs: DownloadJob[] = [];
    for (const object of Object.values(index.objects)) {
      const prefix = object.hash.slice(0, 2);
      jobs.push({
        url: `${RESOURCES_URL}/${prefix}/${object.hash}`,
        path: join(this.paths.assets, 'objects', prefix, object.hash),
        sha1: object.hash,
        size: object.size,
        localSource: reuseFrom ? join(reuseFrom, 'assets', 'objects', prefix, object.hash) : undefined
      });
    }
    return { indexId: indexInfo.id, jobs, index };
  }
}
