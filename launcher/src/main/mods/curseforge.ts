import type { ModCategory, ModDependencyType, ModSearchQuery, ModSort, ModSummary, ModTargetInfo } from '../../common/types';
import { fetchJson, request } from '../net/http';
import type { ModProvider, ProviderVersion } from './provider';

const API = 'https://api.curseforge.com/v1';
const MINECRAFT = 432;
const MODS_CLASS = 6;
/** CurseForge mod loader ids. Legacy Fabric mods are published as Fabric there. */
const LOADER_ID: Record<string, number> = { fabric: 4, 'legacy-fabric': 4 };
const LOADER_NAMES: Record<number, string> = { 1: 'forge', 4: 'fabric', 5: 'quilt', 6: 'neoforge' };
const SORT: Record<ModSort, number> = { relevance: 2, downloads: 6, updated: 3, newest: 11 };
const RELEASE: Record<number, 'release' | 'beta' | 'alpha'> = { 1: 'release', 2: 'beta', 3: 'alpha' };
const RELATION: Record<number, ModDependencyType> = { 1: 'embedded', 2: 'optional', 3: 'required', 4: 'optional', 5: 'incompatible', 6: 'embedded' };

interface CfMod {
  id: number;
  name: string;
  slug: string;
  summary: string;
  links: { websiteUrl: string };
  logo: { thumbnailUrl: string; url: string } | null;
  authors: { name: string }[];
  categories: { name: string; classId?: number }[];
  downloadCount: number;
  dateModified: string;
  latestFilesIndexes: { gameVersion: string; modLoader?: number }[];
  allowModDistribution: boolean | null;
}

interface CfFile {
  id: number;
  modId: number;
  displayName: string;
  fileName: string;
  releaseType: number;
  fileDate: string;
  fileLength: number;
  downloadUrl: string | null;
  gameVersions: string[];
  dependencies: { modId: number; relationType: number }[];
  hashes: { value: string; algo: number }[];
}

const isGameVersion = (v: string) => /^\d+\.\d+(\.\d+)?$/.test(v);

/** Whether a file runs on the target: the Minecraft version and Fabric among its tags. */
export function curseforgeCompatible(file: { gameVersions: string[] }, target: ModTargetInfo): boolean {
  const tags = file.gameVersions.map((v) => v.toLowerCase());
  return file.gameVersions.includes(target.minecraftVersion) && tags.includes('fabric');
}

const byVersion = (a: string, b: string) => {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pb[i] ?? 0) - (pa[i] ?? 0);
    if (d !== 0) {
      return d;
    }
  }
  return 0;
};

/**
 * CurseForge (https://docs.curseforge.com/rest-api): needs an API key from the CurseForge for
 * Studios console, sent as x-api-key. Files whose authors do not allow third-party downloads have
 * no download URL; Medirian links to their page instead of pretending to install them.
 */
export class CurseForgeProvider implements ModProvider {
  readonly id = 'curseforge' as const;
  readonly name = 'CurseForge';

  private readonly apiKey: () => string;

  constructor(apiKey: () => string) {
    this.apiKey = apiKey;
  }

  unavailableReason(): string | null {
    return this.apiKey() ? null : 'CurseForge needs an API key (Settings → Mods).';
  }

  private get<T>(path: string): Promise<T> {
    const key = this.apiKey();
    if (!key) {
      throw new Error(this.unavailableReason()!);
    }
    return fetchJson<T>(`${API}${path}`, { init: { headers: { 'x-api-key': key, Accept: 'application/json' } } });
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    const response = await request(`${API}${path}`, {
      retries: 1,
      init: { method: 'POST', headers: { 'x-api-key': this.apiKey(), Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
    });
    return (await response.json()) as T;
  }

  private summary(mod: CfMod): ModSummary {
    const versions = [...new Set(mod.latestFilesIndexes.map((i) => i.gameVersion).filter(isGameVersion))].sort(byVersion);
    const loaders = [...new Set(mod.latestFilesIndexes.map((i) => LOADER_NAMES[i.modLoader ?? 0]).filter(Boolean))];
    return {
      source: 'curseforge',
      projectId: String(mod.id),
      slug: mod.slug,
      name: mod.name,
      description: mod.summary,
      author: mod.authors[0]?.name ?? '',
      iconUrl: mod.logo?.thumbnailUrl || null,
      downloads: mod.downloadCount,
      categories: mod.categories.map((c) => c.name),
      gameVersions: versions,
      loaders,
      pageUrl: mod.links.websiteUrl,
      updatedAt: mod.dateModified
    };
  }

  async search(query: ModSearchQuery, target: ModTargetInfo): Promise<{ hits: ModSummary[]; total: number }> {
    const params = new URLSearchParams({
      gameId: String(MINECRAFT),
      classId: String(MODS_CLASS),
      searchFilter: query.query,
      gameVersion: target.minecraftVersion,
      modLoaderType: String(LOADER_ID[target.loader] ?? 4),
      sortField: String(SORT[query.sort]),
      sortOrder: 'desc',
      index: String(query.page * query.pageSize),
      pageSize: String(Math.min(50, query.pageSize))
    });
    if (query.category) {
      params.set('categoryId', query.category);
    }
    const result = await this.get<{ data: CfMod[]; pagination: { totalCount: number } }>(`/mods/search?${params}`);
    // CurseForge pages stop at 10 000 results
    return { hits: result.data.map((mod) => this.summary(mod)), total: Math.min(result.pagination.totalCount, 10_000) };
  }

  async categories(): Promise<ModCategory[]> {
    const result = await this.get<{ data: { id: number; name: string; classId?: number; isClass?: boolean }[] }>(
      `/categories?gameId=${MINECRAFT}&classId=${MODS_CLASS}`
    );
    return result.data
      .filter((c) => !c.isClass)
      .map((c) => ({ id: String(c.id), name: c.name }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async project(projectId: string): Promise<ModSummary> {
    return this.summary((await this.get<{ data: CfMod }>(`/mods/${encodeURIComponent(projectId)}`)).data);
  }

  async projects(projectIds: string[]): Promise<ModSummary[]> {
    if (projectIds.length === 0) {
      return [];
    }
    const result = await this.post<{ data: CfMod[] }>('/mods', { modIds: projectIds.map(Number) });
    return result.data.map((mod) => this.summary(mod));
  }

  private toVersion(file: CfFile, target: ModTargetInfo, websiteUrl?: string): ProviderVersion {
    const tags = file.gameVersions.map((v) => v.toLowerCase());
    return {
      id: `${file.modId}:${file.id}`,
      projectId: String(file.modId),
      versionNumber: file.displayName.replace(/\.jar$/i, ''),
      name: file.displayName,
      gameVersions: file.gameVersions.filter(isGameVersion).sort(byVersion),
      loaders: ['fabric', 'forge', 'quilt', 'neoforge'].filter((l) => tags.includes(l)),
      releaseType: RELEASE[file.releaseType] ?? 'release',
      publishedAt: file.fileDate,
      fileName: file.fileName,
      size: file.fileLength,
      compatible: curseforgeCompatible(file, target),
      dependencies: file.dependencies.map((d) => ({ projectId: String(d.modId), type: RELATION[d.relationType] ?? 'optional' })),
      url: file.downloadUrl,
      sha1: file.hashes.find((h) => h.algo === 1)?.value ?? null,
      pageUrl: websiteUrl ? `${websiteUrl}/files/${file.id}` : undefined
    };
  }

  async versions(projectId: string, target: ModTargetInfo, onlyCompatible: boolean): Promise<ProviderVersion[]> {
    const params = new URLSearchParams({ pageSize: '50' });
    if (onlyCompatible) {
      params.set('gameVersion', target.minecraftVersion);
      params.set('modLoaderType', String(LOADER_ID[target.loader] ?? 4));
    }
    const [files, mod] = await Promise.all([
      this.get<{ data: CfFile[] }>(`/mods/${encodeURIComponent(projectId)}/files?${params}`),
      this.get<{ data: CfMod }>(`/mods/${encodeURIComponent(projectId)}`)
    ]);
    return files.data
      .map((file) => this.toVersion(file, target, mod.data.links.websiteUrl))
      .filter((v) => !onlyCompatible || v.compatible)
      .sort((a, b) => b.publishedAt.localeCompare(a.publishedAt));
  }

  async version(versionId: string, target: ModTargetInfo): Promise<ProviderVersion> {
    const [modId, fileId] = versionId.split(':');
    const [file, mod] = await Promise.all([
      this.get<{ data: CfFile }>(`/mods/${modId}/files/${fileId}`),
      this.get<{ data: CfMod }>(`/mods/${modId}`)
    ]);
    return this.toVersion(file.data, target, mod.data.links.websiteUrl);
  }
}
