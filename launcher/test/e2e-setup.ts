// E2E fixture (not a unit test): two Minecraft 1.21.8 profiles in MEDIRIAN_HOME, Mod Menu (with
// its dependencies) installed from Modrinth into the first one only, through the launcher's real
// ModService and ModrinthProvider.
//
// Usage: MEDIRIAN_HOME=<dir> node --experimental-strip-types --import ./test/ts-resolve.mjs test/e2e-setup.ts
import { join } from 'node:path';
import { ModService } from '../src/main/mods/mods.ts';
import { ModrinthProvider } from '../src/main/mods/modrinth.ts';
import { setUserAgent } from '../src/main/net/http.ts';
import { ProfileStore } from '../src/main/profiles/profiles.ts';

const home = process.env.MEDIRIAN_HOME;
if (!home) {
  throw new Error('MEDIRIAN_HOME is required');
}
setUserAgent('0.4.0', 'e2e test');
const store = new ProfileStore(join(home, 'launcher', 'profiles.json'), join(home, 'profiles'), join(home, 'instances'));
await store.load();
const find = async (name: string) => store.list().find((p) => p.name === name) ?? store.create({ name, targetId: '1.21.8' });
const withMods = await find('E2E Mods 1.21.8');
const clean = await find('E2E Clean 1.21.8');

const modrinth = new ModrinthProvider();
const mods = new ModService({
  profiles: store,
  target: () => ({ minecraftVersion: '1.21.8', loader: 'fabric', loaderName: 'Fabric' }),
  isRunning: () => false,
  catalog: modrinth,
  concurrency: () => 4,
  emitTask: (task) => task && console.log(`  ${task.label} ${task.done}/${task.total}`)
});

const plan = await mods.plan(withMods.id, 'modmenu');
console.log('plan:', plan.steps.map((s) => `${s.name} ${s.versionNumber} (${s.reason})`).join(', '), plan.problems);
const state = await mods.install(withMods.id, 'modmenu');
console.log(`"${withMods.name}" (${store.directoryOf(withMods)}):`, state.mods.map((m) => `${m.file} [${m.enabled ? 'on' : 'off'}]`).join(', '));
console.log(`"${clean.name}" (${store.directoryOf(clean)}):`, (await mods.installed(clean.id)).mods.length, 'mods');
