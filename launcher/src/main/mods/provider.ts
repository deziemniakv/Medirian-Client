import type { ModCategory, ModSearchQuery, ModSummary, ModTargetInfo, ModVersionInfo } from '../../common/types';

/** A version of a mod as the launcher needs it to install the file. */
export interface ProviderVersion extends ModVersionInfo {
  projectId: string;
  url: string;
  sha1: string | null;
}

/** The mod catalogue (Modrinth) behind an interface, so the tests can use a fake one. */
export interface ModCatalog {
  search(query: ModSearchQuery, target: ModTargetInfo): Promise<{ hits: ModSummary[]; total: number }>;
  categories(): Promise<ModCategory[]>;
  project(projectId: string): Promise<ModSummary>;
  /** Several projects at once (dependency names); unknown ids are skipped. */
  projects(projectIds: string[]): Promise<ModSummary[]>;
  /** Versions newest first; with onlyCompatible just the ones for the target. */
  versions(projectId: string, target: ModTargetInfo, onlyCompatible: boolean): Promise<ProviderVersion[]>;
  version(versionId: string, target: ModTargetInfo): Promise<ProviderVersion>;
  /** Versions of files by SHA-1 (recognises jars added by hand). */
  identify(sha1s: string[], target: ModTargetInfo): Promise<Map<string, ProviderVersion>>;
  /** The newest compatible version for each file, by SHA-1. */
  latestFor(sha1s: string[], target: ModTargetInfo): Promise<Map<string, ProviderVersion>>;
}

/** The newest release that fits the target, else the newest beta, else the newest alpha. */
export function bestVersion(versions: ProviderVersion[]): ProviderVersion | null {
  const compatible = versions.filter((v) => v.compatible);
  for (const type of ['release', 'beta', 'alpha'] as const) {
    const found = compatible.find((v) => v.releaseType === type);
    if (found) {
      return found;
    }
  }
  return null;
}
