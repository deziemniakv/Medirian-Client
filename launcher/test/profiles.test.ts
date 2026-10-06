import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { ProfileStore, folderName, uniqueFolder } from '../src/main/profiles/profiles.ts';
import { parseTextures } from '../src/main/skins/skins.ts';

test('profile folder names are safe on Windows and unique', () => {
  assert.equal(folderName('My PvP Profile'), 'My PvP Profile');
  assert.equal(folderName('a<b>:c?*'), 'a b c');
  assert.equal(folderName('con'), 'Profile');
  assert.equal(folderName('ends with dots...'), 'ends with dots');
  assert.equal(uniqueFolder('PvP', ['pvp', 'PvP (2)']), 'PvP (3)');
});

test('profiles from before 0.2.0 move their shared version folder into the first profile', async () => {
  const home = await mkdtemp(join(tmpdir(), 'medirian-profiles-'));
  try {
    const file = join(home, 'launcher', 'profiles.json');
    await mkdir(join(home, 'launcher'), { recursive: true });
    await mkdir(join(home, 'instances', '1.8.9', 'saves', 'World'), { recursive: true });
    await writeFile(file, JSON.stringify({
      version: 1,
      profiles: [
        { id: 'a', name: 'PvP 1.8.9', targetId: '1.8.9', memoryMb: 2048, javaPath: null, jvmArgs: '', resolution: null, configProfile: null, createdAt: 1, lastPlayed: null },
        { id: 'b', name: 'Bedwars', targetId: '1.8.9', memoryMb: 2048, javaPath: null, jvmArgs: '', resolution: null, configProfile: null, createdAt: 2, lastPlayed: null }
      ]
    }));
    const store = new ProfileStore(file, join(home, 'profiles'), join(home, 'instances'));
    await store.load();
    const [first, second] = store.list();
    assert.equal(first.directory, 'PvP 1.8.9');
    assert.equal(second.directory, 'Bedwars');
    assert.ok(existsSync(join(home, 'profiles', 'PvP 1.8.9', 'saves', 'World')), 'worlds moved with the first profile');
    assert.ok(!existsSync(join(home, 'instances', '1.8.9')));
    // renaming never moves the folder
    await store.save({ ...first, name: 'Renamed' });
    assert.equal(store.get('a')!.directory, 'PvP 1.8.9');
    const saved = JSON.parse(await readFile(file, 'utf8')) as { profiles: { directory: string }[] };
    assert.deepEqual(saved.profiles.map((p) => p.directory), ['PvP 1.8.9', 'Bedwars']);
  } finally {
    await rm(home, { recursive: true, force: true });
  }
});

test('the skin URL and arm model come from the session profile textures', () => {
  const value = Buffer.from(JSON.stringify({
    textures: { SKIN: { url: 'http://textures.minecraft.net/texture/abc', metadata: { model: 'slim' } } }
  })).toString('base64');
  assert.deepEqual(parseTextures([{ name: 'textures', value }]), { url: 'https://textures.minecraft.net/texture/abc', model: 'slim' });
  assert.deepEqual(parseTextures([]), { url: null, model: 'classic' });
  const noSkin = Buffer.from(JSON.stringify({ textures: {} })).toString('base64');
  assert.deepEqual(parseTextures([{ name: 'textures', value: noSkin }]), { url: null, model: 'classic' });
});
