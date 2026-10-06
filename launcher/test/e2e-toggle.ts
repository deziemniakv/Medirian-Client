// E2E fixture (not a unit test): disables Mod Menu and removes Text Placeholder API in the
// "E2E Mods 1.21.8" profile made by e2e-setup.ts, through the launcher's real ModService.
//
// Usage: MEDIRIAN_HOME=<dir> node --experimental-strip-types --import ./test/ts-resolve.mjs test/e2e-toggle.ts
import { join } from 'node:path';
import { ModService } from '../src/main/mods/mods.ts';
import { ModrinthProvider } from '../src/main/mods/modrinth.ts';
import type { ModProvider } from '../src/main/mods/provider.ts';
import { setUserAgent } from '../src/main/net/http.ts';
import { ProfileStore } from '../src/main/profiles/profiles.ts';

const home = process.env.MEDIRIAN_HOME;
if (!home) {
  throw new Error('MEDIRIAN_HOME is required');
}
setUserAgent('0.3.0');
const store = new ProfileStore(join(home, 'launcher', 'profiles.json'), join(home, 'profiles'), join(home, 'instances'));
await store.load();
const profile = store.list().find((p) => p.name === 'E2E Mods 1.21.8');
if (!profile) {
  throw new Error('run e2e-setup.ts first');
}
const modrinth = new ModrinthProvider();
const mods = new ModService({
  profiles: store,
  target: () => ({ minecraftVersion: '1.21.8', loader: 'fabric', loaderName: 'Fabric' }),
  isRunning: () => false,
  providers: { modrinth, curseforge: { unavailableReason: () => 'not used here' } as unknown as ModProvider },
  modrinth,
  concurrency: () => 4,
  emitTask: () => undefined
});
const before = await mods.installed(profile.id);
const file = (name: string) => before.mods.find((m) => m.name === name)!.file;
await mods.setEnabled(profile.id, file('Mod Menu'), false);
const after = await mods.remove(profile.id, file('Text Placeholder API'));
for (const m of after.mods) {
  console.log(`${m.name} ${m.versionNumber}: ${m.enabled ? 'enabled' : 'disabled'}${m.dependencies.some((d) => !d.installed) ? ' (missing: ' + m.dependencies.filter((d) => !d.installed).map((d) => d.name).join(', ') + ')' : ''}`);
}
