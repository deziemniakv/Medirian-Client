import { readdir, rm, stat } from 'node:fs/promises';
import { join } from 'node:path';
import type { DiskUsage } from '../../common/types';
import type { MeridianPaths } from '../core/paths';

async function directorySize(dir: string): Promise<number> {
  let total = 0;
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return 0;
  }
  for (const entry of entries) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      total += await directorySize(path);
    } else if (entry.isFile()) {
      total += (await stat(path).catch(() => ({ size: 0 }))).size;
    }
  }
  return total;
}

export async function diskUsage(paths: MeridianPaths): Promise<DiskUsage> {
  const [runtimeBytes, gameBytes, clientsBytes, cacheBytes] = await Promise.all([
    directorySize(paths.runtime),
    directorySize(paths.game),
    directorySize(paths.clients),
    directorySize(paths.cache)
  ]);
  return { runtimeBytes, gameBytes, clientsBytes, cacheBytes };
}

/**
 * Clears re-creatable data: download cache, extracted natives and leftover partial downloads.
 * Game files, worlds, configuration and installed runtimes are kept.
 */
export async function clearCache(paths: MeridianPaths): Promise<number> {
  const freed = await directorySize(paths.cache);
  await rm(paths.cache, { recursive: true, force: true });
  return freed;
}
