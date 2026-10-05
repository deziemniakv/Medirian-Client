import { existsSync } from 'node:fs';
import { join } from 'node:path';
import type { LoaderType } from '../../common/types';
import { readJson, writeJson } from '../core/json';
import type { MedirianPaths } from '../core/paths';
import { fetchJson, fetchText } from '../net/http';
import type { Argument, Library } from './mojang';
import { mavenPath } from './maven';

/** Launch profile of a mod loader as served by Fabric / Legacy Fabric meta. */
export interface LoaderProfile {
  id: string;
  inheritsFrom: string;
  mainClass: string;
  arguments?: { game?: Argument[]; jvm?: Argument[] };
  libraries: Library[];
}

const META: Record<LoaderType, string> = {
  fabric: 'https://meta.fabricmc.net/v2',
  'legacy-fabric': 'https://meta.legacyfabric.net/v2'
};

/**
 * Resolves Fabric / Legacy Fabric launch profiles. Loader versions are pinned by the release
 * manifest, so a profile never changes once downloaded and is cached in game/versions.
 */
export class LoaderService {
  constructor(private readonly paths: MedirianPaths) {}

  async profile(type: LoaderType, minecraftVersion: string, loaderVersion: string): Promise<LoaderProfile> {
    const id = `${type}-loader-${loaderVersion}-${minecraftVersion}`;
    const file = join(this.paths.versions, id, `${id}.json`);
    if (existsSync(file)) {
      const cached = await readJson<LoaderProfile | null>(file, null);
      if (cached && cached.mainClass) {
        return cached;
      }
    }
    const url = `${META[type]}/versions/loader/${encodeURIComponent(minecraftVersion)}/${encodeURIComponent(loaderVersion)}/profile/json`;
    const profile = await fetchJson<LoaderProfile>(url);
    await this.attachChecksums(profile.libraries);
    await writeJson(file, profile);
    return profile;
  }

  /** Loader metadata does not always include hashes; fetch the Maven .sha1 files once. */
  private async attachChecksums(libraries: Library[]): Promise<void> {
    await Promise.all(
      libraries.map(async (library) => {
        if (library.sha1 || library.downloads?.artifact || !library.url) {
          return;
        }
        const url = library.url.replace(/\/?$/, '/') + mavenPath(library.name) + '.sha1';
        try {
          library.sha1 = (await fetchText(url, { retries: 1 })).trim().split(/\s+/)[0];
        } catch {
          // checksum unavailable: the file is still verified by size/existence
        }
      })
    );
  }
}
