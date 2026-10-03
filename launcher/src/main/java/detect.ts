import { execFile } from 'node:child_process';
import { existsSync } from 'node:fs';
import { readdir } from 'node:fs/promises';
import { homedir, platform } from 'node:os';
import { join } from 'node:path';
import { promisify } from 'node:util';
import type { JavaInstall } from '../../common/types';

const run = promisify(execFile);

/** Parses "1.8.0_51" → 8, "21.0.7" → 21. */
export function javaMajor(version: string): number {
  const parts = version.split(/[._-]/).map((part) => parseInt(part, 10));
  return parts[0] === 1 ? parts[1] : parts[0];
}

/** Runs `java -XshowSettings:properties -version` and reads the reported properties. */
export async function probeJava(executable: string, source: JavaInstall['source']): Promise<JavaInstall | null> {
  try {
    const { stderr, stdout } = await run(executable, ['-XshowSettings:properties', '-version'], { timeout: 10_000, windowsHide: true });
    const output = stderr + stdout;
    const property = (name: string) => output.match(new RegExp(`^\\s*${name.replace('.', '\\.')} = (.*)$`, 'm'))?.[1]?.trim() ?? '';
    const version = property('java.version');
    if (!version) {
      return null;
    }
    return {
      path: executable,
      version,
      major: javaMajor(version),
      arch: property('os.arch'),
      vendor: property('java.vendor'),
      source
    };
  } catch {
    return null;
  }
}

function javaBinary(home: string): string {
  return join(home, 'bin', platform() === 'win32' ? 'java.exe' : 'java');
}

async function subdirectories(dir: string): Promise<string[]> {
  try {
    const entries = await readdir(dir, { withFileTypes: true });
    return entries.filter((entry) => entry.isDirectory()).map((entry) => join(dir, entry.name));
  } catch {
    return [];
  }
}

/** Finds Java installations in the usual locations of each operating system. */
export async function detectJavaInstallations(meridianRuntimeDir: string): Promise<JavaInstall[]> {
  const candidates = new Set<string>();
  const add = (home: string) => {
    const binary = javaBinary(home);
    if (existsSync(binary)) {
      candidates.add(binary);
    }
  };
  if (process.env.JAVA_HOME) {
    add(process.env.JAVA_HOME);
  }
  const roots: string[] = [];
  if (platform() === 'win32') {
    const programFiles = [process.env.ProgramFiles, process.env['ProgramFiles(x86)']].filter(Boolean) as string[];
    for (const base of programFiles) {
      for (const vendor of ['Java', 'Eclipse Adoptium', 'Eclipse Foundation', 'Zulu', 'Microsoft', 'BellSoft', 'Amazon Corretto', 'Semeru']) {
        roots.push(join(base, vendor));
      }
    }
    roots.push(join(homedir(), '.jdks'));
    if (process.env.APPDATA) {
      roots.push(join(process.env.APPDATA, '.minecraft', 'runtime'));
    }
  } else if (platform() === 'darwin') {
    for (const home of await subdirectories('/Library/Java/JavaVirtualMachines')) {
      add(join(home, 'Contents', 'Home'));
    }
    roots.push(join(homedir(), '.jdks'));
  } else {
    roots.push('/usr/lib/jvm', '/usr/java', '/opt', join(homedir(), '.jdks'));
  }
  for (const root of roots) {
    for (const home of await subdirectories(root)) {
      add(home);
      // Mojang runtime layout: runtime/<component>/<platform>/<component>/bin/java
      for (const nested of await subdirectories(home)) {
        add(nested);
        for (const deeper of await subdirectories(nested)) {
          add(deeper);
        }
      }
    }
  }
  const meridianRuntimes: string[] = [];
  for (const component of await subdirectories(meridianRuntimeDir)) {
    const binary = platform() === 'darwin' ? join(component, 'jre.bundle', 'Contents', 'Home', 'bin', 'java') : javaBinary(component);
    if (existsSync(binary)) {
      meridianRuntimes.push(binary);
    }
  }
  const probes = await Promise.all([
    ...meridianRuntimes.map((path) => probeJava(path, 'meridian')),
    ...[...candidates].map((path) => probeJava(path, 'system'))
  ]);
  const seen = new Set<string>();
  return probes.filter((java): java is JavaInstall => {
    if (!java || seen.has(java.path.toLowerCase())) {
      return false;
    }
    seen.add(java.path.toLowerCase());
    return true;
  }).sort((a, b) => b.major - a.major);
}
