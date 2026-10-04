import { existsSync } from 'node:fs';
import { chmod, mkdir, readFile, symlink, writeFile } from 'node:fs/promises';
import { arch, platform } from 'node:os';
import { dirname, join } from 'node:path';
import type { MeridianPaths } from '../core/paths';
import { downloadAll, type DownloadProgress } from '../net/downloader';
import { fetchJson } from '../net/http';

const RUNTIMES_URL =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

interface RuntimeEntry {
  manifest: { sha1: string; size: number; url: string };
  version: { name: string; released: string };
}

type RuntimeIndex = Record<string, Record<string, RuntimeEntry[]>>;

interface RuntimeFile {
  type: 'file' | 'directory' | 'link';
  executable?: boolean;
  target?: string;
  downloads?: { raw: { sha1: string; size: number; url: string } };
}

/** Mojang platform keys to try, best match first. */
function platformKeys(): string[] {
  const a = arch();
  switch (platform()) {
    case 'win32':
      return a === 'arm64' ? ['windows-arm64', 'windows-x64'] : a === 'ia32' ? ['windows-x86'] : ['windows-x64'];
    case 'darwin':
      // Java 8 (jre-legacy) has no arm64 build: fall back to x64 under Rosetta
      return a === 'arm64' ? ['mac-os-arm64', 'mac-os'] : ['mac-os'];
    default:
      return a === 'ia32' ? ['linux-i386'] : ['linux'];
  }
}

/**
 * Installs and locates the Java runtimes Mojang publishes for each Minecraft version
 * (e.g. {@code jre-legacy} = Java 8 for 1.8.9, {@code java-runtime-delta} = Java 21,
 * {@code java-runtime-epsilon} = Java 25 for 26.x).
 */
export class JavaRuntimeService {
  constructor(private readonly paths: MeridianPaths) {}

  private runtimeDir(component: string): string {
    return join(this.paths.runtime, component);
  }

  javaExecutable(component: string): string {
    const dir = this.runtimeDir(component);
    if (platform() === 'darwin') {
      return join(dir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
    }
    return join(dir, 'bin', platform() === 'win32' ? 'java.exe' : 'java');
  }

  /** Ensures {@code component} is installed and up to date; returns the java executable. */
  async ensure(component: string, onProgress?: (progress: DownloadProgress) => void, concurrency = 8): Promise<string> {
    let index: RuntimeIndex;
    try {
      index = await fetchJson<RuntimeIndex>(RUNTIMES_URL);
    } catch (error) {
      // offline: an already installed runtime is good enough
      if (existsSync(this.javaExecutable(component))) {
        return this.javaExecutable(component);
      }
      throw error;
    }
    let entry: RuntimeEntry | undefined;
    for (const key of platformKeys()) {
      entry = index[key]?.[component]?.[0];
      if (entry) {
        break;
      }
    }
    if (!entry) {
      throw new Error(`Mojang does not publish Java runtime "${component}" for ${platform()}-${arch()}. Choose a Java installation in the profile settings.`);
    }
    const dir = this.runtimeDir(component);
    const marker = join(dir, '.meridian-runtime');
    const executable = this.javaExecutable(component);
    if (existsSync(marker) && existsSync(executable) && (await readFile(marker, 'utf8')).trim() === entry.manifest.sha1) {
      return executable;
    }
    const manifest = await fetchJson<{ files: Record<string, RuntimeFile> }>(entry.manifest.url);
    const files = Object.entries(manifest.files);
    for (const [path, file] of files) {
      if (file.type === 'directory') {
        await mkdir(join(dir, path), { recursive: true });
      }
    }
    const jobs = files
      .filter(([, file]) => file.type === 'file' && file.downloads)
      .map(([path, file]) => ({
        url: file.downloads!.raw.url,
        path: join(dir, path),
        sha1: file.downloads!.raw.sha1,
        size: file.downloads!.raw.size
      }));
    await downloadAll(jobs, { concurrency, verify: 'full', onProgress });
    if (platform() !== 'win32') {
      for (const [path, file] of files) {
        const target = join(dir, path);
        if (file.type === 'file' && file.executable) {
          await chmod(target, 0o755);
        } else if (file.type === 'link' && file.target && !existsSync(target)) {
          await mkdir(dirname(target), { recursive: true });
          await symlink(file.target, target).catch(() => undefined);
        }
      }
    }
    await writeFile(marker, entry.manifest.sha1, 'utf8');
    return executable;
  }
}
