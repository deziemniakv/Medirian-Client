import { existsSync } from 'node:fs';
import { homedir, platform } from 'node:os';
import { join } from 'node:path';

/**
 * MERIDIAN_HOME resolution. MUST match client/shared/.../core/MeridianHome.java:
 * MERIDIAN_HOME env → per-OS default.
 */
export function resolveMeridianHome(): string {
  const env = process.env.MERIDIAN_HOME?.trim();
  if (env) {
    return env;
  }
  switch (platform()) {
    case 'win32':
      return join(process.env.APPDATA ?? homedir(), '.meridian');
    case 'darwin':
      return join(homedir(), 'Library', 'Application Support', 'meridian');
    default:
      return join(homedir(), '.meridian');
  }
}

/** Default vanilla launcher directory, or null when Minecraft was never installed. */
export function detectMinecraftDir(): string | null {
  let dir: string;
  switch (platform()) {
    case 'win32':
      dir = join(process.env.APPDATA ?? homedir(), '.minecraft');
      break;
    case 'darwin':
      dir = join(homedir(), 'Library', 'Application Support', 'minecraft');
      break;
    default:
      dir = join(homedir(), '.minecraft');
  }
  return existsSync(dir) ? dir : null;
}

export interface MeridianPaths {
  root: string;
  launcher: string;
  settingsFile: string;
  profilesFile: string;
  accountsFile: string;
  logs: string;
  runtime: string;
  game: string;
  versions: string;
  libraries: string;
  assets: string;
  clients: string;
  instances: string;
  config: string;
  clientProfiles: string;
  cache: string;
  natives: string;
}

export function meridianPaths(root: string = resolveMeridianHome()): MeridianPaths {
  const launcher = join(root, 'launcher');
  const game = join(root, 'game');
  const config = join(root, 'config');
  const cache = join(root, 'cache');
  return {
    root,
    launcher,
    settingsFile: join(launcher, 'settings.json'),
    profilesFile: join(launcher, 'profiles.json'),
    accountsFile: join(launcher, 'accounts.json'),
    logs: join(launcher, 'logs'),
    runtime: join(root, 'runtime'),
    game,
    versions: join(game, 'versions'),
    libraries: join(game, 'libraries'),
    assets: join(game, 'assets'),
    clients: join(root, 'clients'),
    instances: join(root, 'instances'),
    config,
    clientProfiles: join(config, 'profiles'),
    cache,
    natives: join(cache, 'natives')
  };
}

/** Game directory of a target (separate per target: mods, options.txt and worlds differ). */
export function instanceDir(paths: MeridianPaths, targetId: string): string {
  return join(paths.instances, targetId);
}
