import type { ModCategory, ModDependencyType, ModSearchQuery, ModSummary, ModTargetInfo } from '../../common/types';
import { fetchJson, request } from '../net/http';
import type { ModCatalog, ProviderVersion } from './provider';

const API = 'https://api.modrinth.com/v2';
const LOADERS = new Set(['fabric', 'legacy-fabric', 'forge', 'neoforge', 'quilt', 'liteloader', 'rift', 'modloader', 'babric', 'ornithe', 'bta-babric', 'java-agent', 'nilloader']);

interface SearchHit {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  icon_url: string | null;
  downloads: number;
  categories: string[];
  display_categories?: string[];
  versions: string[];
  date_modified: string;
}

interface Project {
  id: string;
  slug: string;
  title: string;
  description: string;
  icon_url: string | null;
  downloads: number;
  categories: string[];
  loaders: string[];
  game_versions: string[];
  updated: string;
  team: string;
}

interface Version {
  id: string;
  project_id: string;
  name: string;
  version_number: string;
  game_versions: string[];
  loaders: string[];
  version_type: 'release' | 'beta' | 'alpha';
  date_published: string;
  files: { url: string; filename: string; primary: boolean; size: number; hashes: { sha1?: string; sha512?: string } }[];
  dependencies: { project_id: string | null; version_id: string | null; dependency_type: ModDependencyType }[];
}

const capitalize = (s: string) => s.replace(/(^|-)([a-z])/g, (_, sep: string, c: string) => (sep ? ' ' : '') + c.toUpperCase());

/** Whether a version runs on the target: its Minecraft version and Medirian's loader. */
export function modrinthCompatible(version: { game_versions: string[]; loaders: string[] }, target: ModTargetInfo): boolean {
  return version.game_versions.includes(target.minecraftVersion) && version.loaders.includes(target.loader);
}

/**
 * Modrinth (https://docs.modrinth.com), Medirian's only mod catalogue: a public API that needs no
 * key for searching and downloading. Every request carries Medirian's User-Agent with a contact
 * (net/http.ts, MEDIRIAN_CONTACT), as Modrinth's terms ask.
 */
export class ModrinthProvider implements ModCatalog {
  async search(query: ModSearchQuery, target: ModTargetInfo): Promise<{ hits: ModSummary[]; total: number }> {
    const facets: string[][] = [['project_type:mod'], [`categories:${target.loader}`], [`versions:${target.minecraftVersion}`]];
    if (query.category) {
      facets.push([`categories:${query.category}`]);
    }
    const params = new URLSearchParams({
      query: query.query,
      facets: JSON.stringify(facets),
      index: query.sort,
      offset: String(query.page * query.pageSize),
      limit: String(query.pageSize)
    });
    const result = await fetchJson<{ hits: SearchHit[]; total_hits: number }>(`${API}/search?${params}`);
    return {
      total: result.total_hits,
      hits: result.hits.map((hit) => ({
        projectId: hit.project_id,
        slug: hit.slug,
        name: hit.title,
        description: hit.description,
        author: hit.author,
        iconUrl: hit.icon_url || null,
        downloads: hit.downloads,
        categories: (hit.display_categories ?? hit.categories).filter((c) => !LOADERS.has(c)).map(capitalize),
        gameVersions: [...hit.versions].reverse(),
        loaders: hit.categories.filter((c) => LOADERS.has(c)),
        pageUrl: `https://modrinth.com/mod/${hit.slug}`,
        updatedAt: hit.date_modified
      }))
    };
  }

  async categories(): Promise<ModCategory[]> {
    const tags = await fetchJson<{ name: string; project_type: string; header: string }[]>(`${API}/tag/category`);
    return tags
      .filter((tag) => tag.project_type === 'mod' && tag.header === 'categories')
      .map((tag) => ({ id: tag.name, name: capitalize(tag.name) }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  private summary(project: Project, author: string): ModSummary {
    return {
      projectId: project.id,
      slug: project.slug,
      name: project.title,
      description: project.description,
      author,
      iconUrl: project.icon_url || null,
      downloads: project.downloads,
      categories: project.categories.filter((c) => !LOADERS.has(c)).map(capitalize),
      gameVersions: [...project.game_versions].reverse(),
      loaders: project.loaders,
      pageUrl: `https://modrinth.com/mod/${project.slug}`,
      updatedAt: project.updated
    };
  }

  async project(projectId: string): Promise<ModSummary> {
    const project = await fetchJson<Project>(`${API}/project/${encodeURIComponent(projectId)}`);
    let author = '';
    try {
      const members = await fetchJson<{ user: { username: string }; role: string; ordering?: number }[]>(`${API}/project/${project.id}/members`);
      author = (members.find((m) => m.role === 'Owner') ?? members[0])?.user.username ?? '';
    } catch {
      // the author is only shown, never required
    }
    return this.summary(project, author);
  }

  async projects(projectIds: string[]): Promise<ModSummary[]> {
    if (projectIds.length === 0) {
      return [];
    }
    const projects = await fetchJson<Project[]>(`${API}/projects?ids=${encodeURIComponent(JSON.stringify(projectIds))}`);
    return projects.map((project) => this.summary(project, ''));
  }

  private toVersion(version: Version, target: ModTargetInfo): ProviderVersion {
    const file = version.files.find((f) => f.primary) ?? version.files[0];
    if (!file) {
      throw new Error(`Modrinth version ${version.id} has no file`);
    }
    return {
      id: version.id,
      projectId: version.project_id,
      versionNumber: version.version_number,
      name: version.name,
      gameVersions: version.game_versions,
      loaders: version.loaders,
      releaseType: version.version_type,
      publishedAt: version.date_published,
      fileName: file.filename,
      size: file.size,
      compatible: modrinthCompatible(version, target),
      dependencies: version.dependencies
        .filter((d) => d.project_id || d.version_id)
        .map((d) => ({ projectId: d.project_id ?? '', versionId: d.version_id ?? undefined, type: d.dependency_type })),
      url: file.url,
      sha1: file.hashes.sha1 ?? null
    };
  }

  async versions(projectId: string, target: ModTargetInfo, onlyCompatible: boolean): Promise<ProviderVersion[]> {
    const params = onlyCompatible
      ? `?loaders=${encodeURIComponent(JSON.stringify([target.loader]))}&game_versions=${encodeURIComponent(JSON.stringify([target.minecraftVersion]))}`
      : '';
    const versions = await fetchJson<Version[]>(`${API}/project/${encodeURIComponent(projectId)}/version${params}`);
    return versions
      .filter((v) => v.files.length > 0)
      .map((v) => this.toVersion(v, target))
      .sort((a, b) => b.publishedAt.localeCompare(a.publishedAt));
  }

  async version(versionId: string, target: ModTargetInfo): Promise<ProviderVersion> {
    return this.toVersion(await fetchJson<Version>(`${API}/version/${encodeURIComponent(versionId)}`), target);
  }

  /** Modrinth versions of files, by SHA-1 (identifies jars the user added by hand). */
  async identify(sha1s: string[], target: ModTargetInfo): Promise<Map<string, ProviderVersion>> {
    const out = new Map<string, ProviderVersion>();
    if (sha1s.length === 0) {
      return out;
    }
    const response = await request(`${API}/version_files`, {
      retries: 1,
      init: { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ hashes: sha1s, algorithm: 'sha1' }) }
    });
    const found = (await response.json()) as Record<string, Version>;
    for (const [hash, version] of Object.entries(found)) {
      if (version.files.length > 0) {
        out.set(hash, this.toVersion(version, target));
      }
    }
    return out;
  }

  /** The newest compatible version for each file (by SHA-1); a different id means an update. */
  async latestFor(sha1s: string[], target: ModTargetInfo): Promise<Map<string, ProviderVersion>> {
    const out = new Map<string, ProviderVersion>();
    if (sha1s.length === 0) {
      return out;
    }
    const response = await request(`${API}/version_files/update`, {
      retries: 1,
      init: {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ hashes: sha1s, algorithm: 'sha1', loaders: [target.loader], game_versions: [target.minecraftVersion] })
      }
    });
    const found = (await response.json()) as Record<string, Version>;
    for (const [hash, version] of Object.entries(found)) {
      if (version.files.length > 0) {
        out.set(hash, this.toVersion(version, target));
      }
    }
    return out;
  }
}
