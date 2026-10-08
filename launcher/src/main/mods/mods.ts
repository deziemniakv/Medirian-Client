import { existsSync } from 'node:fs';
import { mkdir, readdir, rename, rm, stat } from 'node:fs/promises';
import { join } from 'node:path';
import type {
  InstalledMod,
  InstalledModsState,
  LaunchProfile,
  ModCategory,
  ModDetails,
  ModInstallPlan,
  ModInstallStep,
  ModIssue,
  ModSearchQuery,
  ModSearchResult,
  ModSource,
  ModTargetInfo,
  ModTask
} from '../../common/types';
import { readJson, writeJson } from '../core/json';
import { log } from '../core/log';
import { readZipEntry } from '../minecraft/zip';
import { downloadAll, sha1File } from '../net/downloader';
import { bestVersion, type ModCatalog, type ProviderVersion } from './provider';

/** Medirian's own jar in every profile's mods folder; managed by the installer, never listed. */
export const MEDIRIAN_JAR = 'medirian-client.jar';
const INDEX = 'medirian-mods.json';
const DISABLED = '.disabled';

interface IndexEntry {
  source: ModSource;
  projectId: string | null;
  versionId: string | null;
  name: string;
  versionNumber: string;
  iconUrl: string | null;
  author: string | null;
  pageUrl: string | null;
  sha1: string;
  size: number;
  /** Fabric mod id from fabric.mod.json. */
  modId: string | null;
  /** Required dependencies: by project (installed from a platform) or by Fabric mod id (local jars). */
  dependencies: { projectId: string | null; modId: string | null; name: string }[];
  installedAt: number;
}

interface IndexFile {
  version: 1;
  mods: Record<string, IndexEntry>;
  updates: Record<string, { versionId: string; versionNumber: string }>;
  checkedAt: number | null;
}

/** Fabric mod ids that are always there (the game, the loader, Java) and never block anything. */
const BUILT_IN = new Set(['minecraft', 'java', 'fabricloader', 'fabric-loader', 'medirian']);

interface FabricModJson {
  id?: string;
  name?: string;
  version?: string;
  authors?: (string | { name?: string })[];
  depends?: Record<string, unknown>;
  contact?: { homepage?: string; sources?: string };
}

/** An issue in English, for errors and the log (the launcher's UI translates the codes). */
export function describeIssue(issue: ModIssue, target: ModTargetInfo): string {
  const where = `Minecraft ${target.minecraftVersion} (${target.loaderName})`;
  switch (issue.code) {
    case 'incompatible':
      return `${issue.name} is not compatible with ${where}.`;
    case 'versionIncompatible':
      return `${issue.name} ${issue.version} is not compatible with ${where}.`;
    case 'dependencyUnavailable':
      return `${issue.name} (required by ${issue.parent}) has no version for ${where}.`;
    case 'alreadyInstalled':
      return `${issue.name} ${issue.version} is already installed in this profile.`;
    case 'dependencyDisabled':
      return `${issue.name} is installed but disabled — it will be enabled.`;
    case 'conflict':
      return `${issue.name} declares itself incompatible with ${issue.other}.`;
  }
}

async function readFabricMod(jar: string): Promise<FabricModJson | null> {
  try {
    const raw = await readZipEntry(jar, 'fabric.mod.json');
    if (!raw) {
      return null;
    }
    // some mods ship comments or trailing commas
    const text = raw.toString('utf8').replace(/^\s*\/\/.*$/gm, '').replace(/,(\s*[}\]])/g, '$1');
    return JSON.parse(text) as FabricModJson;
  } catch {
    return null;
  }
}

export interface ModServiceDeps {
  profiles: { get(id: string): LaunchProfile | undefined; directoryOf(p: LaunchProfile): string };
  /** The Minecraft version and loader of a Medirian target. */
  target(targetId: string): ModTargetInfo;
  /** Whether Minecraft is running with this profile (files are in use). */
  isRunning(profileId: string): boolean;
  /** Modrinth. */
  catalog: ModCatalog;
  concurrency(): number;
  emitTask(task: ModTask | null): void;
}

/**
 * Mods per launch profile: search on Modrinth, compatibility with the profile's
 * Minecraft version and loader, dependency resolution, and install / remove / enable / disable /
 * update in the profile's own mods folder. A small index (medirian-mods.json next to the mods
 * folder) remembers where each jar came from; jars added by hand are recognised by their SHA-1 on
 * Modrinth or by their fabric.mod.json.
 */
export class ModService {
  private readonly locks = new Map<string, Promise<unknown>>();
  private readonly deps: ModServiceDeps;

  constructor(deps: ModServiceDeps) {
    this.deps = deps;
  }

  private profile(profileId: string): LaunchProfile {
    const profile = this.deps.profiles.get(profileId);
    if (!profile) {
      throw new Error('Profile not found.');
    }
    return profile;
  }

  targetOf(profileId: string): ModTargetInfo {
    return this.deps.target(this.profile(profileId).targetId);
  }

  private modsDir(profile: LaunchProfile): string {
    return join(this.deps.profiles.directoryOf(profile), 'mods');
  }

  private indexFile(profile: LaunchProfile): string {
    return join(this.deps.profiles.directoryOf(profile), INDEX);
  }

  /** Runs file operations of one profile one at a time. */
  private exclusive<T>(profileId: string, work: () => Promise<T>): Promise<T> {
    const previous = this.locks.get(profileId) ?? Promise.resolve();
    const next = previous.catch(() => undefined).then(work);
    this.locks.set(profileId, next);
    return next;
  }

  private assertNotRunning(profileId: string): void {
    if (this.deps.isRunning(profileId)) {
      throw new Error('Close Minecraft first: this profile is running and its mods are in use.');
    }
  }

  // ------------------------------------------------------------------ browsing

  async search(query: ModSearchQuery): Promise<ModSearchResult> {
    const target = this.targetOf(query.profileId);
    const { hits, total } = await this.deps.catalog.search(query, target);
    return { hits, total, page: query.page, pageSize: query.pageSize, target };
  }

  categories(): Promise<ModCategory[]> {
    return this.deps.catalog.categories();
  }

  async details(projectId: string, profileId: string): Promise<ModDetails> {
    const target = this.targetOf(profileId);
    const catalog = this.deps.catalog;
    const [summary, versions] = await Promise.all([catalog.project(projectId), catalog.versions(projectId, target, false)]);
    return { ...summary, versions: versions.slice(0, 60), target };
  }

  // ------------------------------------------------------------------ installed mods

  /** Enabled mods in the profile's folder (without Medirian itself); no network. */
  async count(profileId: string): Promise<number> {
    const files = await readdir(this.modsDir(this.profile(profileId))).catch(() => [] as string[]);
    return files.filter((name) => name.toLowerCase().endsWith('.jar') && name !== MEDIRIAN_JAR).length;
  }

  async installed(profileId: string): Promise<InstalledModsState> {
    return this.exclusive(profileId, () => this.scan(this.profile(profileId)));
  }

  /** Reads the mods folder, recognises new jars and returns the profile's mods. */
  private async scan(profile: LaunchProfile): Promise<InstalledModsState> {
    const target = this.deps.target(profile.targetId);
    const dir = this.modsDir(profile);
    await mkdir(dir, { recursive: true });
    const index = await this.readIndex(profile);
    let changed = false;

    const files = (await readdir(dir)).filter((name) => {
      const base = name.endsWith(DISABLED) ? name.slice(0, -DISABLED.length) : name;
      return base.toLowerCase().endsWith('.jar') && base !== MEDIRIAN_JAR;
    });
    const present = new Map<string, { enabled: boolean; size: number }>();
    for (const name of files) {
      const enabled = !name.endsWith(DISABLED);
      const base = enabled ? name : name.slice(0, -DISABLED.length);
      const info = await stat(join(dir, name));
      present.set(base, { enabled, size: info.size });
    }
    for (const base of Object.keys(index.mods)) {
      if (!present.has(base)) {
        delete index.mods[base];
        delete index.updates[base];
        changed = true;
      }
    }

    // jars that are new (or replaced) since the last scan
    const unknown: { base: string; path: string; size: number; sha1: string }[] = [];
    for (const [base, { enabled, size }] of present) {
      const entry = index.mods[base];
      if (!entry || entry.size !== size) {
        const path = join(dir, enabled ? base : base + DISABLED);
        unknown.push({ base, path, size, sha1: await sha1File(path) });
      }
    }
    if (unknown.length > 0) {
      changed = true;
      let identified = new Map<string, ProviderVersion>();
      try {
        identified = await this.deps.catalog.identify(unknown.map((u) => u.sha1), target);
      } catch (error) {
        log.warn('Could not look mods up on Modrinth', error);
      }
      const projectIds = [...new Set([...identified.values()].flatMap((v) => [v.projectId, ...this.requiredIds(v)]))];
      const projects = await this.deps.catalog.projects(projectIds).catch(() => []);
      const names = new Map(projects.map((p) => [p.projectId, p]));
      for (const file of unknown) {
        const fabric = await readFabricMod(file.path);
        const version = identified.get(file.sha1);
        const project = version ? names.get(version.projectId) : undefined;
        index.mods[file.base] = version
          ? {
              source: 'modrinth',
              projectId: version.projectId,
              versionId: version.id,
              name: project?.name ?? fabric?.name ?? file.base,
              versionNumber: version.versionNumber,
              iconUrl: project?.iconUrl ?? null,
              author: project?.author || null,
              pageUrl: project?.pageUrl ?? null,
              sha1: file.sha1,
              size: file.size,
              modId: fabric?.id ?? null,
              dependencies: this.requiredIds(version).map((id) => ({ projectId: id, modId: null, name: names.get(id)?.name ?? id })),
              installedAt: Date.now()
            }
          : this.localEntry(file, fabric);
      }
    }
    if (changed) {
      await writeJson(this.indexFile(profile), index);
    }
    return { profileId: profile.id, target, mods: this.view(index, present), checkedAt: index.checkedAt };
  }

  /**
   * The profile's index. Mods recorded by 0.3.0 as coming from CurseForge (no longer supported)
   * are kept as local files.
   */
  private async readIndex(profile: LaunchProfile): Promise<IndexFile> {
    const index = await readJson<IndexFile>(this.indexFile(profile), { version: 1, mods: {}, updates: {}, checkedAt: null });
    index.updates ??= {};
    for (const [file, entry] of Object.entries(index.mods)) {
      if (entry.source !== 'modrinth' && entry.source !== 'local') {
        index.mods[file] = { ...entry, source: 'local', projectId: null, versionId: null, pageUrl: null, dependencies: [] };
        delete index.updates[file];
      }
    }
    return index;
  }

  private requiredIds(version: ProviderVersion): string[] {
    return version.dependencies.filter((d) => d.type === 'required' && d.projectId).map((d) => d.projectId);
  }

  private localEntry(file: { base: string; size: number; sha1: string }, fabric: FabricModJson | null): IndexEntry {
    const author = fabric?.authors?.map((a) => (typeof a === 'string' ? a : a.name ?? '')).find(Boolean) ?? null;
    return {
      source: 'local',
      projectId: null,
      versionId: null,
      name: fabric?.name ?? file.base.replace(/\.jar$/i, ''),
      versionNumber: fabric?.version ?? '',
      iconUrl: null,
      author,
      pageUrl: fabric?.contact?.homepage ?? null,
      sha1: file.sha1,
      size: file.size,
      modId: fabric?.id ?? null,
      dependencies: Object.keys(fabric?.depends ?? {})
        .filter((id) => !BUILT_IN.has(id))
        .map((id) => ({ projectId: null, modId: id, name: id })),
      installedAt: Date.now()
    };
  }

  /** The renderer's view: dependencies resolved against what is installed, and who needs what. */
  private view(index: IndexFile, present: Map<string, { enabled: boolean; size: number }>): InstalledMod[] {
    const entries = Object.entries(index.mods);
    const provides = (entry: IndexEntry, dep: { projectId: string | null; modId: string | null }) =>
      (dep.projectId !== null && entry.projectId === dep.projectId) || (dep.modId !== null && entry.modId === dep.modId)
      // Fabric API's modules count as Fabric API
      || (dep.modId !== null && dep.modId.startsWith('fabric-') && entry.modId === 'fabric-api');
    return entries
      .map(([file, entry]) => ({
        file,
        enabled: present.get(file)?.enabled ?? true,
        name: entry.name,
        versionNumber: entry.versionNumber,
        source: entry.source,
        projectId: entry.projectId,
        versionId: entry.versionId,
        iconUrl: entry.iconUrl,
        author: entry.author,
        pageUrl: entry.pageUrl,
        size: entry.size,
        dependencies: entry.dependencies.map((dep) => ({
          name: dep.name,
          installed: entries.some(([other, e]) => other !== file && provides(e, dep))
        })),
        requiredBy: entries
          .filter(([other, e]) => other !== file && e.dependencies.some((dep) => provides(entry, dep)))
          .map(([, e]) => e.name),
        installedAt: entry.installedAt,
        update: index.updates[file] ?? null
      }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async setEnabled(profileId: string, file: string, enabled: boolean): Promise<InstalledModsState> {
    return this.exclusive(profileId, async () => {
      this.assertNotRunning(profileId);
      const profile = this.profile(profileId);
      const dir = this.modsDir(profile);
      const from = join(dir, enabled ? file + DISABLED : file);
      const to = join(dir, enabled ? file : file + DISABLED);
      if (existsSync(from)) {
        await rename(from, to);
      }
      return this.scan(profile);
    });
  }

  async remove(profileId: string, file: string): Promise<InstalledModsState> {
    return this.exclusive(profileId, async () => {
      this.assertNotRunning(profileId);
      const profile = this.profile(profileId);
      if (file === MEDIRIAN_JAR) {
        throw new Error('Medirian itself cannot be removed.');
      }
      const dir = this.modsDir(profile);
      await rm(join(dir, file), { force: true });
      await rm(join(dir, file + DISABLED), { force: true });
      return this.scan(profile);
    });
  }

  // ------------------------------------------------------------------ installing

  /**
   * What installing a project (a given version, or the best one for the profile) does: the file,
   * every required dependency that is not in the profile yet, and anything that blocks it.
   */
  async plan(profileId: string, projectId: string, versionId?: string): Promise<ModInstallPlan> {
    const profile = this.profile(profileId);
    const state = await this.installed(profileId);
    return this.buildPlan(profile, state, projectId, versionId);
  }

  private async buildPlan(profile: LaunchProfile, state: InstalledModsState, projectId: string, versionId?: string): Promise<ModInstallPlan> {
    const target = state.target;
    const provider = this.deps.catalog;
    const plan: ModInstallPlan = { profileId: profile.id, target, steps: [], satisfied: [], problems: [], warnings: [] };
    const installedProject = (id: string) => state.mods.find((m) => m.source === 'modrinth' && m.projectId === id);
    const queue: { projectId: string; versionId?: string; reason: ModInstallStep['reason']; parent?: string }[] = [
      { projectId, versionId, reason: 'requested' }
    ];
    const seen = new Set<string>();
    const names = new Map<string, string>();
    const nameOf = async (id: string) => {
      if (!names.has(id)) {
        names.set(id, await provider.project(id).then((p) => p.name).catch(() => id));
      }
      return names.get(id)!;
    };

    while (queue.length > 0) {
      const item = queue.shift()!;
      if (!item.projectId && item.versionId) {
        item.projectId = (await provider.version(item.versionId, target)).projectId;
      }
      if (seen.has(item.projectId)) {
        continue;
      }
      seen.add(item.projectId);
      const name = await nameOf(item.projectId);
      const already = installedProject(item.projectId);
      if (item.reason === 'dependency' && already) {
        plan.satisfied.push(already.name);
        if (!already.enabled) {
          plan.warnings.push({ code: 'dependencyDisabled', name: already.name });
        }
        continue;
      }

      let version: ProviderVersion | null = null;
      if (item.versionId) {
        const pinned = await provider.version(item.versionId, target).catch(() => null);
        if (pinned?.compatible) {
          version = pinned;
        } else if (item.reason === 'requested' && pinned) {
          plan.problems.push({ code: 'versionIncompatible', name, version: pinned.versionNumber });
          continue;
        }
      }
      if (!version) {
        version = bestVersion(await provider.versions(item.projectId, target, true));
      }
      if (!version) {
        plan.problems.push(item.reason === 'requested'
          ? { code: 'incompatible', name }
          : { code: 'dependencyUnavailable', name, parent: item.parent ?? '' });
        continue;
      }
      if (item.reason === 'requested' && already && already.versionId === version.id) {
        plan.problems.push({ code: 'alreadyInstalled', name, version: version.versionNumber });
        continue;
      }
      plan.steps.push({
        projectId: item.projectId,
        versionId: version.id,
        name,
        versionNumber: version.versionNumber,
        fileName: version.fileName,
        size: version.size,
        reason: item.reason
      });
      for (const dep of version.dependencies) {
        if (dep.type === 'required') {
          queue.push({ projectId: dep.projectId, versionId: dep.versionId, reason: 'dependency', parent: name });
        } else if (dep.type === 'incompatible' && dep.projectId && installedProject(dep.projectId)) {
          plan.warnings.push({ code: 'conflict', name, other: installedProject(dep.projectId)!.name });
        }
      }
    }
    return plan;
  }

  /** Plans and installs a project with its missing dependencies into the profile. */
  async install(profileId: string, projectId: string, versionId?: string): Promise<InstalledModsState> {
    return this.exclusive(profileId, async () => {
      this.assertNotRunning(profileId);
      const profile = this.profile(profileId);
      const state = await this.scan(profile);
      const plan = await this.buildPlan(profile, state, projectId, versionId);
      if (plan.problems.length > 0) {
        throw new Error(plan.problems.map((p) => describeIssue(p, plan.target)).join(' '));
      }
      await this.execute(profile, state, plan);
      return this.scan(profile);
    });
  }

  private async execute(profile: LaunchProfile, state: InstalledModsState, plan: ModInstallPlan): Promise<void> {
    const dir = this.modsDir(profile);
    const provider = this.deps.catalog;
    const target = state.target;
    const versions: ProviderVersion[] = [];
    for (const step of plan.steps) {
      versions.push(await provider.version(step.versionId, target));
    }
    const total = versions.length;
    this.deps.emitTask({ profileId: profile.id, label: `Downloading ${plan.steps[0]?.name ?? 'mods'}`, done: 0, total });
    try {
      await downloadAll(
        versions.map((v) => ({ url: v.url, path: join(dir, v.fileName), sha1: v.sha1 ?? undefined, size: v.size || undefined })),
        {
          concurrency: Math.min(4, this.deps.concurrency()),
          verify: 'full',
          onProgress: (p) => this.deps.emitTask({ profileId: profile.id, label: `Downloading ${plan.steps[0]?.name ?? 'mods'}`, done: p.done, total: p.total })
        }
      );
    } finally {
      this.deps.emitTask(null);
    }

    const index = await this.readIndex(profile);
    const depIds = [...new Set(versions.flatMap((v) => this.requiredIds(v)))];
    const projects = await provider.projects([...new Set([...plan.steps.map((s) => s.projectId), ...depIds])]).catch(() => []);
    const info = new Map(projects.map((p) => [p.projectId, p]));
    for (let i = 0; i < versions.length; i++) {
      const version = versions[i];
      const step = plan.steps[i];
      // an update replaces the older file of the same project
      const old = state.mods.find((m) => m.source === 'modrinth' && m.projectId === step.projectId && m.file !== version.fileName);
      if (old) {
        await rm(join(dir, old.file), { force: true });
        await rm(join(dir, old.file + DISABLED), { force: true });
        delete index.mods[old.file];
        delete index.updates[old.file];
      }
      const path = join(dir, version.fileName);
      const fabric = await readFabricMod(path);
      const project = info.get(step.projectId);
      index.mods[version.fileName] = {
        source: 'modrinth',
        projectId: step.projectId,
        versionId: version.id,
        name: project?.name ?? step.name,
        versionNumber: version.versionNumber,
        iconUrl: project?.iconUrl ?? null,
        author: project?.author || null,
        pageUrl: project?.pageUrl ?? null,
        sha1: version.sha1 ?? (await sha1File(path)),
        size: (await stat(path)).size,
        modId: fabric?.id ?? null,
        dependencies: this.requiredIds(version).map((id) => ({ projectId: id, modId: null, name: info.get(id)?.name ?? id })),
        installedAt: Date.now()
      };
      delete index.updates[version.fileName];
    }
    // required dependencies that were disabled get switched back on
    for (const name of plan.satisfied) {
      const mod = state.mods.find((m) => m.name === name && !m.enabled);
      if (mod && existsSync(join(dir, mod.file + DISABLED))) {
        await rename(join(dir, mod.file + DISABLED), join(dir, mod.file));
      }
    }
    await writeJson(this.indexFile(profile), index);
    log.info(`Installed into "${profile.name}": ${plan.steps.map((s) => `${s.name} ${s.versionNumber}`).join(', ')}`);
  }

  // ------------------------------------------------------------------ updates

  /** Looks for newer compatible versions of the profile's mods. */
  async checkUpdates(profileId: string): Promise<InstalledModsState> {
    return this.exclusive(profileId, async () => {
      const profile = this.profile(profileId);
      const state = await this.scan(profile);
      const target = state.target;
      const index = await this.readIndex(profile);
      index.updates = {};
      const fromModrinth = Object.entries(index.mods).filter(([, e]) => e.source === 'modrinth');
      const latest = await this.deps.catalog.latestFor(fromModrinth.map(([, e]) => e.sha1), target);
      for (const [file, entry] of fromModrinth) {
        const newer = latest.get(entry.sha1);
        if (newer && newer.compatible && newer.id !== entry.versionId && newer.projectId === entry.projectId) {
          index.updates[file] = { versionId: newer.id, versionNumber: newer.versionNumber };
        }
      }
      index.checkedAt = Date.now();
      await writeJson(this.indexFile(profile), index);
      return this.scan(profile);
    });
  }

  /** Installs the newer version found by checkUpdates (with any new dependencies). */
  async update(profileId: string, file: string): Promise<InstalledModsState> {
    const state = await this.installed(profileId);
    const mod = state.mods.find((m) => m.file === file);
    if (!mod || mod.source !== 'modrinth' || !mod.projectId) {
      throw new Error('Only mods from Modrinth can be updated here.');
    }
    if (!mod.update) {
      throw new Error(`${mod.name} is up to date.`);
    }
    return this.install(profileId, mod.projectId, mod.update.versionId);
  }
}
