import type { ModCategory, ModSearchQuery, ModSource, ModSummary, ModTargetInfo, ModVersionInfo } from '../../common/types';

/** A version of a mod as the launcher needs it to install the file. */
export interface ProviderVersion extends ModVersionInfo {
  projectId: string;
  /** Download URL; null when the author does not allow downloads from other apps (CurseForge). */
  url: string | null;
  sha1: string | null;
  /** Web page of this file, for mods that must be downloaded by hand. */
  pageUrl?: string;
}

/** A mod platform (Modrinth, CurseForge) behind one interface. */
export interface ModProvider {
  readonly id: ModSource;
  readonly name: string;
  /** null when usable; otherwise why not (e.g. a missing API key). */
  unavailableReason(): string | null;
  search(query: ModSearchQuery, target: ModTargetInfo): Promise<{ hits: ModSummary[]; total: number }>;
  categories(): Promise<ModCategory[]>;
  project(projectId: string): Promise<ModSummary>;
  /** Several projects at once (dependency names); unknown ids are skipped. */
  projects(projectIds: string[]): Promise<ModSummary[]>;
  /** Versions newest first; with onlyCompatible just the ones for the target. */
  versions(projectId: string, target: ModTargetInfo, onlyCompatible: boolean): Promise<ProviderVersion[]>;
  version(versionId: string, target: ModTargetInfo): Promise<ProviderVersion>;
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
