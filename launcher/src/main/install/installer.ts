import { existsSync } from 'node:fs';
import { copyFile, mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import { delimiter, join } from 'node:path';
import type { LaunchProfile, ReleaseTarget, RepairReport, TaskProgress } from '../../common/types';
import { log } from '../core/log';
import { detectMinecraftDir, instanceDir, type MeridianPaths } from '../core/paths';
import { probeJava } from '../java/detect';
import type { JavaRuntimeService } from '../java/runtime';
import { DEFAULT_GC_ARGS, expandArguments, mergeJvmArgs, splitArgs, substitute } from '../launch/arguments';
import type { LoaderService } from '../minecraft/loader';
import { libraryKey } from '../minecraft/maven';
import type { Argument, Library, MojangService, VersionJson } from '../minecraft/mojang';
import { extractZip } from '../minecraft/zip';
import { downloadAll, type DownloadJob, type DownloadProgress, type VerifyMode } from '../net/downloader';
import type { UpdateService } from '../updates/updates';
import type { LaunchSession } from '../auth/accounts';

export interface LaunchPlan {
  java: string;
  args: string[];
  gameDir: string;
  mainClass: string;
}

export interface LaunchContext {
  profile: LaunchProfile;
  target: ReleaseTarget;
  session: LaunchSession;
  launcherVersion: string;
  bridge: { port: number; token: string } | null;
  /** Meridian services for the client (-Dmeridian.api); empty = services off. */
  servicesUrl?: string;
}

type ProgressFn = (progress: TaskProgress) => void;

/**
 * Prepares everything a target needs (Java, Minecraft, loader, libraries, natives, assets,
 * Meridian jar) and builds the final command line. Re-running it is cheap: valid files are
 * skipped, so the same code path is used for first install, updates and launches.
 */
export class Installer {
  constructor(
    private readonly paths: MeridianPaths,
    private readonly mojang: MojangService,
    private readonly loader: LoaderService,
    private readonly runtimes: JavaRuntimeService,
    private readonly updates: UpdateService,
    private readonly concurrency: () => number,
    private readonly reuseAssets: () => boolean
  ) {}

  async prepare(ctx: LaunchContext, onProgress: ProgressFn): Promise<LaunchPlan> {
    const { profile, target } = ctx;
    const report = (phase: TaskProgress['phase'], label: string) => (p: DownloadProgress) =>
      onProgress({ phase, label, done: p.done, total: p.total, bytesDone: p.bytesDone, bytesTotal: p.bytesTotal });

    // 1. Java
    onProgress({ phase: 'java', label: 'Preparing Java', done: 0, total: 1 });
    const java = profile.javaPath
      ? await this.validateCustomJava(profile.javaPath, target)
      : await this.runtimes.ensure(target.java.component, report('java', `Installing Java ${target.java.majorVersion}`), this.concurrency());

    // 2. Minecraft + loader metadata
    onProgress({ phase: 'version', label: `Loading Minecraft ${target.minecraftVersion}`, done: 0, total: 1 });
    const version = await this.mojang.versionJson(target.minecraftVersion);
    onProgress({ phase: 'loader', label: 'Loading mod loader', done: 0, total: 1 });
    const loaderProfile = await this.loader.profile(target.loader.type, target.minecraftVersion, target.loader.version);

    // 3. Libraries + client jar
    const libraries = mergeLibraries(version.libraries, loaderProfile.libraries);
    const resolved = this.mojang.resolveLibraries(libraries);
    const clientJar = this.mojang.clientJar(version);
    await downloadAll([...resolved.jobs, clientJar], {
      concurrency: this.concurrency(),
      verify: 'quick',
      onProgress: report('libraries', 'Downloading libraries')
    });

    // 4. Natives (LWJGL 2 only; LWJGL 3 extracts its own)
    const nativesDir = join(this.paths.natives, version.id);
    if (resolved.natives.length > 0) {
      onProgress({ phase: 'natives', label: 'Extracting natives', done: 0, total: resolved.natives.length });
      await this.extractNatives(nativesDir, resolved.natives);
    }

    // 5. Assets
    const reuseFrom = this.reuseAssets() ? detectMinecraftDir() : null;
    const assets = await this.mojang.assetJobs(version, reuseFrom);
    await downloadAll(assets.jobs, {
      concurrency: this.concurrency(),
      verify: 'quick',
      onProgress: report('assets', 'Downloading assets')
    });
    const gameDir = instanceDir(this.paths, target.id);
    await mkdir(gameDir, { recursive: true });
    const assetsRoot = await this.prepareLegacyAssets(assets.index, assets.indexId, gameDir);

    // 6. Meridian
    const clientPath = await this.updates.ensureClient(target, report('client', 'Installing Meridian'));
    await this.installMod(gameDir, clientPath);

    // 7. Command line
    onProgress({ phase: 'launch', label: 'Starting Minecraft', done: 1, total: 1 });
    const classpath = [...resolved.classpath, clientJar.path];
    return {
      java,
      args: this.buildArgs(ctx, version, loaderProfile.mainClass, loaderProfile.arguments, classpath, nativesDir, gameDir, assetsRoot, assets.indexId),
      gameDir,
      mainClass: loaderProfile.mainClass
    };
  }

  private async validateCustomJava(path: string, target: ReleaseTarget): Promise<string> {
    const java = await probeJava(path, 'system');
    if (!java) {
      throw new Error(`The Java executable "${path}" does not work. Choose another one in the profile.`);
    }
    const required = target.java.majorVersion;
    const ok = target.branch === 'legacy' ? java.major === 8 : java.major >= required;
    if (!ok) {
      throw new Error(target.branch === 'legacy'
        ? `Minecraft ${target.minecraftVersion} requires Java 8, but the selected Java is ${java.version}.`
        : `Minecraft ${target.minecraftVersion} requires Java ${required} or newer, but the selected Java is ${java.version}.`);
    }
    return path;
  }

  private async extractNatives(dir: string, natives: { path: string; exclude: string[] }[]): Promise<void> {
    const marker = join(dir, '.extracted');
    if (existsSync(marker)) {
      return;
    }
    await rm(dir, { recursive: true, force: true });
    await mkdir(dir, { recursive: true });
    for (const native of natives) {
      await extractZip(native.path, dir, native.exclude);
    }
    await writeFile(marker, new Date().toISOString());
  }

  /** Pre-1.7 asset layouts ("virtual"/"map_to_resources"); 1.8.9 and newer use objects directly. */
  private async prepareLegacyAssets(index: { objects: Record<string, { hash: string }>; virtual?: boolean; map_to_resources?: boolean }, indexId: string, gameDir: string): Promise<string> {
    if (!index.virtual && !index.map_to_resources) {
      return this.paths.assets;
    }
    const root = index.map_to_resources ? join(gameDir, 'resources') : join(this.paths.assets, 'virtual', indexId);
    for (const [name, object] of Object.entries(index.objects)) {
      const target = join(root, name);
      if (!existsSync(target)) {
        await mkdir(join(target, '..'), { recursive: true });
        await copyFile(join(this.paths.assets, 'objects', object.hash.slice(0, 2), object.hash), target);
      }
    }
    return root;
  }

  /** Copies the Meridian jar into the instance's mods folder, removing older Meridian jars. */
  private async installMod(gameDir: string, clientPath: string): Promise<void> {
    const mods = join(gameDir, 'mods');
    await mkdir(mods, { recursive: true });
    for (const file of await readdir(mods)) {
      if (file.startsWith('meridian-') && file.endsWith('.jar')) {
        await rm(join(mods, file), { force: true });
      }
    }
    await copyFile(clientPath, join(mods, 'meridian-client.jar'));
  }

  private buildArgs(
    ctx: LaunchContext,
    version: VersionJson,
    mainClass: string,
    loaderArgs: { game?: Argument[]; jvm?: Argument[] } | undefined,
    classpath: string[],
    nativesDir: string,
    gameDir: string,
    assetsRoot: string,
    assetIndex: string
  ): string[] {
    const { profile, session, target } = ctx;
    const features = {
      is_demo_user: false,
      has_custom_resolution: profile.resolution !== null,
      has_quick_plays_support: false,
      is_quick_play_singleplayer: false,
      is_quick_play_multiplayer: false,
      is_quick_play_realms: false
    };
    const vars: Record<string, string> = {
      auth_player_name: session.name,
      version_name: version.id,
      game_directory: gameDir,
      assets_root: assetsRoot,
      game_assets: assetsRoot,
      assets_index_name: assetIndex,
      auth_uuid: session.uuid,
      auth_access_token: session.accessToken,
      auth_session: session.accessToken,
      clientid: session.clientId,
      auth_xuid: session.xuid,
      user_type: session.userType,
      version_type: 'release',
      user_properties: '{}',
      resolution_width: String(profile.resolution?.width ?? 854),
      resolution_height: String(profile.resolution?.height ?? 480),
      launcher_name: 'meridian',
      launcher_version: ctx.launcherVersion,
      natives_directory: nativesDir,
      library_directory: this.paths.libraries,
      classpath: classpath.join(delimiter),
      classpath_separator: delimiter
    };

    const jvm: string[] = version.arguments?.jvm
      ? expandArguments(version.arguments.jvm, features)
      : ['-Djava.library.path=${natives_directory}', '-cp', '${classpath}'];
    jvm.push(...expandArguments(loaderArgs?.jvm, features));
    const memory = profile.memoryMb;
    jvm.push(`-Xms${Math.min(1024, memory)}M`, `-Xmx${memory}M`, ...DEFAULT_GC_ARGS);
    jvm.push(
      `-Dmeridian.home=${this.paths.root}`,
      `-Dmeridian.target=${target.id}`,
      ...(profile.configProfile ? [`-Dmeridian.profile=${profile.configProfile}`] : []),
      ...(ctx.bridge ? [`-Dmeridian.launcher.port=${ctx.bridge.port}`, `-Dmeridian.launcher.token=${ctx.bridge.token}`] : []),
      ...(ctx.servicesUrl ? [`-Dmeridian.api=${ctx.servicesUrl}`] : [])
    );

    const game: string[] = version.arguments?.game
      ? expandArguments(version.arguments.game, features)
      : splitArgs(version.minecraftArguments ?? '');
    game.push(...expandArguments(loaderArgs?.game, features));
    if (!version.arguments?.game && profile.resolution) {
      game.push('--width', '${resolution_width}', '--height', '${resolution_height}');
    }
    const finalJvm = mergeJvmArgs(substitute(jvm, vars), splitArgs(profile.jvmArgs));
    return [...finalJvm, mainClass, ...substitute(game, vars)];
  }

  /** Full SHA-1 verification of everything a target uses; re-downloads broken files. */
  async repair(target: ReleaseTarget, onProgress: ProgressFn): Promise<RepairReport> {
    const start = Date.now();
    const version = await this.mojang.versionJson(target.minecraftVersion);
    const loaderProfile = await this.loader.profile(target.loader.type, target.minecraftVersion, target.loader.version);
    const resolved = this.mojang.resolveLibraries(mergeLibraries(version.libraries, loaderProfile.libraries));
    const assets = await this.mojang.assetJobs(version, null);
    const jobs: DownloadJob[] = [...resolved.jobs, this.mojang.clientJar(version), ...assets.jobs];
    const verify: VerifyMode = 'full';
    const result = await downloadAll(jobs, {
      concurrency: this.concurrency(),
      verify,
      onProgress: (p) => onProgress({ phase: 'verify', label: 'Verifying files', done: p.done, total: p.total })
    });
    await rm(join(this.paths.natives, version.id), { recursive: true, force: true });
    log.info(`Repair of ${target.id}: ${result.downloaded} of ${result.checked} files replaced`);
    return { targetId: target.id, checkedFiles: result.checked, repairedFiles: result.downloaded, durationMs: Date.now() - start };
  }
}

/** Vanilla libraries + loader libraries; when both provide the same artifact the loader wins. */
export function mergeLibraries(vanilla: Library[], loader: Library[]): Library[] {
  const loaderKeys = new Set(loader.map((library) => libraryKey(library.name)));
  return [...vanilla.filter((library) => library.natives || !loaderKeys.has(libraryKey(library.name))), ...loader];
}
