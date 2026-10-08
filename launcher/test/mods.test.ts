import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';
import type { LaunchProfile, ModSearchQuery, ModSummary, ModTargetInfo } from '../src/common/types.ts';
import { ModService, describeIssue } from '../src/main/mods/mods.ts';
import type { ModCatalog, ProviderVersion } from '../src/main/mods/provider.ts';
import { ProfileStore } from '../src/main/profiles/profiles.ts';

// ------------------------------------------------------------------ a tiny fake mod platform

const FILES = new Map<string, Buffer>();
let server: Server;
let base = '';

interface FakeVersion {
  id: string;
  projectId: string;
  versionNumber: string;
  gameVersions: string[];
  requires?: string[];
  incompatibleWith?: string[];
}

const PROJECTS: Record<string, string> = { sodium: 'Sodium', 'fabric-api': 'Fabric API', oldpvp: 'Old PvP' };
const VERSIONS: FakeVersion[] = [
  { id: 'sodium-2', projectId: 'sodium', versionNumber: '0.7.0', gameVersions: ['1.21.8'], requires: ['fabric-api'] },
  { id: 'sodium-1', projectId: 'sodium', versionNumber: '0.6.0', gameVersions: ['1.21.8'], requires: ['fabric-api'] },
  { id: 'fapi-1', projectId: 'fabric-api', versionNumber: '0.130.0', gameVersions: ['1.21.8'] },
  { id: 'oldpvp-1', projectId: 'oldpvp', versionNumber: '1.0', gameVersions: ['1.8.9'] }
];

const fileName = (v: FakeVersion) => `${v.projectId}-${v.versionNumber}.jar`;

function providerVersion(v: FakeVersion, target: ModTargetInfo): ProviderVersion {
  const body = FILES.get(fileName(v))!;
  return {
    id: v.id,
    projectId: v.projectId,
    versionNumber: v.versionNumber,
    name: v.versionNumber,
    gameVersions: v.gameVersions,
    loaders: ['fabric'],
    releaseType: 'release',
    publishedAt: '2026-01-01T00:00:00Z',
    fileName: fileName(v),
    size: body.length,
    compatible: v.gameVersions.includes(target.minecraftVersion) && target.loader === 'fabric',
    dependencies: [
      ...(v.requires ?? []).map((projectId) => ({ projectId, type: 'required' as const })),
      ...(v.incompatibleWith ?? []).map((projectId) => ({ projectId, type: 'incompatible' as const }))
    ],
    url: `${base}/${fileName(v)}`,
    sha1: createHash('sha1').update(body).digest('hex')
  };
}

function summary(projectId: string): ModSummary {
  return {
    projectId, slug: projectId, name: PROJECTS[projectId] ?? projectId, description: '', author: 'Someone',
    iconUrl: null, downloads: 1, categories: [], gameVersions: [], loaders: ['fabric'], pageUrl: `https://example.invalid/${projectId}`,
    updatedAt: '2026-01-01T00:00:00Z'
  };
}

/** Modrinth, offline. */
class FakeCatalog implements ModCatalog {
  async search(_query: ModSearchQuery): Promise<{ hits: ModSummary[]; total: number }> {
    return { hits: Object.keys(PROJECTS).map(summary), total: Object.keys(PROJECTS).length };
  }
  async categories() {
    return [];
  }
  async project(projectId: string) {
    return summary(projectId);
  }
  async projects(ids: string[]) {
    return ids.filter((id) => PROJECTS[id]).map(summary);
  }
  async versions(projectId: string, target: ModTargetInfo, onlyCompatible: boolean) {
    return VERSIONS.filter((v) => v.projectId === projectId).map((v) => providerVersion(v, target)).filter((v) => !onlyCompatible || v.compatible);
  }
  async version(versionId: string, target: ModTargetInfo) {
    const found = VERSIONS.find((v) => v.id === versionId);
    if (!found) {
      throw new Error('no such version');
    }
    return providerVersion(found, target);
  }
  // the Modrinth extras ModService uses for hand-added jars and updates
  async identify(): Promise<Map<string, ProviderVersion>> {
    return new Map();
  }
  async latestFor(sha1s: string[], target: ModTargetInfo): Promise<Map<string, ProviderVersion>> {
    const newest = new Map<string, ProviderVersion>();
    for (const v of VERSIONS) {
      const pv = providerVersion(v, target);
      if (sha1s.includes(pv.sha1!)) {
        const best = VERSIONS.find((o) => o.projectId === v.projectId && o.gameVersions.includes(target.minecraftVersion))!;
        newest.set(pv.sha1!, providerVersion(best, target));
      }
    }
    return newest;
  }
}

// ------------------------------------------------------------------ fixture

const TARGETS: Record<string, ModTargetInfo> = {
  '1.21.8': { minecraftVersion: '1.21.8', loader: 'fabric', loaderName: 'Fabric' },
  '1.8.9': { minecraftVersion: '1.8.9', loader: 'legacy-fabric', loaderName: 'Legacy Fabric' }
};

let home = '';
let store: ProfileStore;
let mods: ModService;
let a: LaunchProfile;
let b: LaunchProfile;
let pvp: LaunchProfile;
const running = new Set<string>();

before(async () => {
  for (const v of VERSIONS) {
    FILES.set(fileName(v), Buffer.from(`jar of ${v.id} `.repeat(40)));
  }
  server = createServer((req, res) => {
    const body = FILES.get(decodeURIComponent((req.url ?? '/').slice(1)));
    res.writeHead(body ? 200 : 404).end(body);
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

  home = await mkdtemp(join(tmpdir(), 'medirian-mods-'));
  store = new ProfileStore(join(home, 'launcher', 'profiles.json'), join(home, 'profiles'), join(home, 'instances'));
  await store.load();
  a = await store.create({ name: 'My PvP Profile', targetId: '1.21.8' });
  b = await store.create({ name: 'Other 1.21.8', targetId: '1.21.8' });
  pvp = await store.create({ name: 'Old PvP', targetId: '1.8.9' });
  mods = new ModService({
    profiles: store,
    target: (id) => TARGETS[id] ?? TARGETS['1.21.8'],
    isRunning: (id) => running.has(id),
    catalog: new FakeCatalog(),
    concurrency: () => 4,
    emitTask: () => undefined
  });
});

after(async () => {
  server.close();
  await rm(home, { recursive: true, force: true });
});

const files = async (p: LaunchProfile) => {
  const dir = join(store.directoryOf(p), 'mods');
  return existsSync(dir) ? (await readdir(dir)).sort() : [];
};

// ------------------------------------------------------------------ tests

test('every profile has its own game folder named after it', () => {
  assert.equal(store.directoryOf(a), join(home, 'profiles', 'My PvP Profile'));
  assert.notEqual(store.directoryOf(a), store.directoryOf(b));
});

test('a mod made for another Minecraft version is refused with a clear reason', async () => {
  const plan = await mods.plan(pvp.id, 'sodium');
  assert.deepEqual(plan.problems, [{ code: 'incompatible', name: 'Sodium' }]);
  assert.equal(describeIssue(plan.problems[0], plan.target), 'Sodium is not compatible with Minecraft 1.8.9 (Legacy Fabric).');
  assert.equal(plan.steps.length, 0);
});

test('required dependencies are planned and installed with the mod, into that profile only', async () => {
  const plan = await mods.plan(a.id, 'sodium');
  assert.deepEqual(plan.problems, []);
  assert.deepEqual(plan.steps.map((s) => [s.name, s.reason]), [['Sodium', 'requested'], ['Fabric API', 'dependency']]);

  const state = await mods.install(a.id, 'sodium');
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar', 'sodium-0.7.0.jar']);
  assert.deepEqual(await files(b), [], 'the other profile of the same version stays clean');
  const sodium = state.mods.find((m) => m.name === 'Sodium')!;
  assert.deepEqual(sodium.dependencies, [{ name: 'Fabric API', installed: true }]);
  assert.deepEqual(state.mods.find((m) => m.name === 'Fabric API')!.requiredBy, ['Sodium']);
  assert.ok(existsSync(join(store.directoryOf(a), 'medirian-mods.json')));
  assert.equal((await mods.installed(b.id)).mods.length, 0);
});

test('installing the same version again is reported, not repeated', async () => {
  const plan = await mods.plan(a.id, 'sodium');
  assert.deepEqual(plan.problems, [{ code: 'alreadyInstalled', name: 'Sodium', version: '0.7.0' }]);
});

test('disabling renames the jar, enabling restores it, nothing is deleted', async () => {
  let state = await mods.setEnabled(a.id, 'fabric-api-0.130.0.jar', false);
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar.disabled', 'sodium-0.7.0.jar']);
  assert.equal(state.mods.find((m) => m.name === 'Fabric API')!.enabled, false);
  // a plan that needs the disabled dependency says it will switch it back on
  await mods.remove(a.id, 'sodium-0.7.0.jar');
  const plan = await mods.plan(a.id, 'sodium');
  assert.deepEqual(plan.warnings, [{ code: 'dependencyDisabled', name: 'Fabric API' }]);
  assert.deepEqual(plan.satisfied, ['Fabric API']);
  await mods.install(a.id, 'sodium');
  state = await mods.installed(a.id);
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar', 'sodium-0.7.0.jar']);
  state = await mods.setEnabled(a.id, 'fabric-api-0.130.0.jar', true);
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar', 'sodium-0.7.0.jar']);
  assert.equal(state.mods.every((m) => m.enabled), true);
});

test('nothing changes while Minecraft runs with the profile', async () => {
  running.add(a.id);
  try {
    await assert.rejects(mods.remove(a.id, 'sodium-0.7.0.jar'), /Close Minecraft first/);
    await assert.rejects(mods.setEnabled(a.id, 'sodium-0.7.0.jar', false), /Close Minecraft first/);
  } finally {
    running.delete(a.id);
  }
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar', 'sodium-0.7.0.jar']);
});

test('an older version is found as an update and replaced by the newer file', async () => {
  await mods.remove(a.id, 'sodium-0.7.0.jar');
  await mods.install(a.id, 'sodium', 'sodium-1');
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar', 'sodium-0.6.0.jar']);
  const checked = await mods.checkUpdates(a.id);
  assert.deepEqual(checked.mods.find((m) => m.name === 'Sodium')!.update, { versionId: 'sodium-2', versionNumber: '0.7.0' });
  const updated = await mods.update(a.id, 'sodium-0.6.0.jar');
  assert.deepEqual(await files(a), ['fabric-api-0.130.0.jar', 'sodium-0.7.0.jar']);
  assert.equal(updated.mods.find((m) => m.name === 'Sodium')!.update, null);
});

test('removing deletes the jar and the dependency shows as missing', async () => {
  const state = await mods.remove(a.id, 'fabric-api-0.130.0.jar');
  assert.deepEqual(await files(a), ['sodium-0.7.0.jar']);
  assert.deepEqual(state.mods[0].dependencies, [{ name: 'Fabric API', installed: false }]);
});

test('search goes to Modrinth for the profile\'s version and loader', async () => {
  const result = await mods.search({ query: '', profileId: pvp.id, category: null, sort: 'relevance', page: 0, pageSize: 20 } as ModSearchQuery);
  assert.equal(result.target.loader, 'legacy-fabric');
  assert.equal(result.hits.length, 3);
});

test('jars added by hand are listed from their file', async () => {
  await mods.installed(b.id);
  await writeFile(join(store.directoryOf(b), 'mods', 'handmade.jar'), 'not really a jar');
  const state = await mods.installed(b.id);
  assert.deepEqual(state.mods.map((m) => [m.file, m.source, m.name]), [['handmade.jar', 'local', 'handmade']]);
});

test('mods 0.3.0 installed from CurseForge stay as local files', async () => {
  const dir = store.directoryOf(b);
  await writeFile(join(dir, 'mods', 'jei.jar'), 'jei');
  const index = JSON.parse(await readFile(join(dir, 'medirian-mods.json'), 'utf8'));
  index.mods['jei.jar'] = {
    source: 'curseforge', projectId: '238222', versionId: '238222:5101', name: 'JEI', versionNumber: '19.0', iconUrl: null,
    author: 'mezz', pageUrl: 'https://www.curseforge.com/minecraft/mc-mods/jei', sha1: 'x', size: 3, modId: 'jei',
    dependencies: [], installedAt: 1
  };
  await writeFile(join(dir, 'medirian-mods.json'), JSON.stringify(index));
  const jei = (await mods.installed(b.id)).mods.find((m) => m.file === 'jei.jar')!;
  assert.deepEqual([jei.source, jei.projectId, jei.pageUrl, jei.name], ['local', null, null, 'JEI']);
  await assert.rejects(mods.update(b.id, 'jei.jar'), /Only mods from Modrinth/);
});

test('a duplicated profile copies its mods into a new folder', async () => {
  const copy = await store.duplicate(a.id, 'My PvP Profile');
  assert.equal(store.directoryOf(copy), join(home, 'profiles', 'My PvP Profile (2)'));
  assert.deepEqual(await files(copy), ['sodium-0.7.0.jar']);
  await mods.remove(copy.id, 'sodium-0.7.0.jar');
  assert.deepEqual(await files(a), ['sodium-0.7.0.jar'], 'the original keeps its mods');
});
