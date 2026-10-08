import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import type { Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';
// the real Medirian Services, in this process
import { createServer } from '../../backend/src/server.mjs';
import { ProfileStore } from '../src/main/profiles/profiles.ts';
import { configName, normalizeCode, ProfileShareService } from '../src/main/profiles/share.ts';

let home = '';
let server: Server;
let store: ProfileStore;
let shares: ProfileShareService;
const running = new Set<string>();

const CONFIG = {
  version: 1, name: 'PvP',
  settings: { hudScale: 0.8, performanceProfile: 'BALANCED' },
  modules: {
    cps: { enabled: true, keybind: 'NONE', settings: { color: '#FF9B55D6' }, hud: { x: 4, y: 4, scale: 0.75 } },
    zoom: { enabled: false, keybind: 'C', settings: {} }
  }
};

before(async () => {
  home = await mkdtemp(join(tmpdir(), 'medirian-share-'));
  server = createServer({ dataDir: join(home, 'services') });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const servicesUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  store = new ProfileStore(join(home, 'launcher', 'profiles.json'), join(home, 'profiles'), join(home, 'instances'));
  await store.load();
  await mkdir(join(home, 'config', 'profiles'), { recursive: true });
  await writeFile(join(home, 'config', 'profiles', 'pvp.json'), JSON.stringify(CONFIG));
  await writeFile(join(home, 'config', 'client.json'), JSON.stringify({
    version: 1, activeProfile: 'PvP', settings: { language: 'PL_PL', font: 'SMOOTH', animations: false }
  }));
  shares = new ProfileShareService({
    servicesUrl,
    configDir: join(home, 'config', 'profiles'),
    clientFile: join(home, 'config', 'client.json'),
    sharesFile: join(home, 'launcher', 'shares.json'),
    profiles: store,
    isRunning: (id) => running.has(id)
  });
});

after(async () => {
  server.close();
  await rm(home, { recursive: true, force: true });
});

test('codes are read the way people type them', () => {
  assert.equal(normalizeCode('mdn 7k4x 92qp r8f2'), 'MDN-7K4X-92QP-R8F2');
  assert.equal(normalizeCode('7K4X92QPR8F2'), 'MDN-7K4X-92QP-R8F2');
  assert.equal(normalizeCode('MDN-7K4X-92QP-L8F2'), null, 'L is not in the alphabet (looks like 1)');
  assert.equal(normalizeCode('hello'), null);
  assert.equal(configName('Bed/Wars: Pro!! setup with a long name'), 'Bed Wars Pro setup with');
});

test('export → code → import as a new profile brings the settings, modules, HUD and video options', async () => {
  const source = await store.create({ name: 'Bedwars Pro', targetId: '1.8.9', configProfile: 'PvP', memoryMb: 3072, jvmArgs: '-javaagent:evil.jar' });
  await mkdir(store.directoryOf(source), { recursive: true });
  await writeFile(join(store.directoryOf(source), 'options.txt'), 'renderDistance:8\nmaxFps:240\nkey_key.jump:key.keyboard.space\nlastServer:play.example.net\n');

  const exported = await shares.export(source.id);
  assert.ok(exported.ok, JSON.stringify(exported));
  const code = exported.value.code;
  assert.match(code, /^MDN-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$/);

  const preview = await shares.preview(code.toLowerCase().replace(/-/g, ' '));
  assert.ok(preview.ok);
  assert.deepEqual([preview.value.name, preview.value.targetId, preview.value.memoryMb, preview.value.modules, preview.value.enabledModules, preview.value.gameOptions],
    ['Bedwars Pro', '1.8.9', 3072, 2, 1, 2]);
  assert.equal(preview.value.existingProfileId, source.id, 'a profile with this name exists here');

  const imported = await shares.apply(code, 'new', null, false);
  assert.ok(imported.ok);
  const profile = imported.value;
  assert.equal(profile.name, 'Bedwars Pro (2)');
  assert.notEqual(profile.id, source.id);
  assert.equal(profile.jvmArgs, '', 'JVM arguments are never shared');
  assert.equal(profile.memoryMb, 3072);
  const config = JSON.parse(await readFile(join(home, 'config', 'profiles', 'bedwars-pro.json'), 'utf8'));
  assert.equal(profile.configProfile, 'Bedwars Pro');
  assert.deepEqual(config.modules, CONFIG.modules, 'modules, keybinds, settings and HUD positions');
  assert.deepEqual(config.settings, CONFIG.settings);
  const options = await readFile(join(store.directoryOf(profile), 'options.txt'), 'utf8');
  assert.match(options, /renderDistance:8/);
  assert.match(options, /maxFps:240/);
  assert.doesNotMatch(options, /lastServer|key_key/, 'only video options travel');
  const client = JSON.parse(await readFile(join(home, 'config', 'client.json'), 'utf8'));
  assert.equal(client.settings.font, 'SMOOTH', 'client settings untouched unless asked');
});

test('importing over an existing profile replaces its settings, and client settings only when asked', async () => {
  const target = await store.create({ name: 'Mine', targetId: '1.21.8' });
  const source = store.list().find((p) => p.name === 'Bedwars Pro')!;
  const code = (await shares.export(source.id) as { ok: true; value: { code: string } }).value.code;
  await shares.preview(code);
  running.add(target.id);
  assert.deepEqual(await shares.apply(code, 'replace', target.id, true), { ok: false, error: 'running' });
  running.delete(target.id);
  await shares.preview(code);
  const replaced = await shares.apply(code, 'replace', target.id, true);
  assert.ok(replaced.ok);
  assert.equal(replaced.value.id, target.id);
  assert.equal(replaced.value.name, 'Mine', 'the replaced profile keeps its name');
  assert.equal(replaced.value.targetId, '1.8.9');
  assert.equal(replaced.value.configProfile, 'Mine');
  assert.ok(existsSync(join(home, 'config', 'profiles', 'mine.json')));
  const client = JSON.parse(await readFile(join(home, 'config', 'client.json'), 'utf8'));
  assert.equal(client.settings.font, 'SMOOTH');
  assert.equal(client.settings.language, 'PL_PL', 'the language is personal and never shared');
});

test('invalid, unknown and deleted codes, and builds without services, say so', async () => {
  assert.deepEqual(await shares.preview('nope'), { ok: false, error: 'invalid' });
  assert.deepEqual(await shares.preview('MDN-2222-3333-4444'), { ok: false, error: 'notFound' });
  const source = store.list().find((p) => p.name === 'Bedwars Pro')!;
  const code = (await shares.export(source.id) as { ok: true; value: { code: string } }).value.code;
  assert.deepEqual(await shares.delete(code), { ok: true, value: null });
  assert.deepEqual(await shares.preview(code), { ok: false, error: 'notFound' });
  assert.deepEqual(await shares.delete('MDN-2222-3333-4444'), { ok: false, error: 'forbidden' }, 'only codes made here can be deleted');
  const offline = new ProfileShareService({ ...(shares as unknown as { deps: ConstructorParameters<typeof ProfileShareService>[0] }).deps, servicesUrl: '' });
  assert.deepEqual(await offline.preview('MDN-2222-3333-4444'), { ok: false, error: 'unconfigured' });
});
