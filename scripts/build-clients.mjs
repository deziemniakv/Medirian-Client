#!/usr/bin/env node
// Builds every Medirian client target and writes the "local" release channel:
//   distribution/release-manifest.json + one jar per target.
//
// Usage: node scripts/build-clients.mjs [--skip-build] [--base-url https://cdn.example.com/medirian/0.1.0/]
//   --skip-build  package the jars that are already built
//   --base-url    also emit absolute artifact URLs (for publishing to a server / stable channel)

import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const skipBuild = args.includes('--skip-build');
const baseUrlIndex = args.indexOf('--base-url');
const baseUrl = baseUrlIndex >= 0 ? args[baseUrlIndex + 1].replace(/\/?$/, '/') : null;

/** Every client target. Adding a Minecraft version = adding an entry here (see docs/ARCHITECTURE.md §12). */
const TARGETS = [
  {
    id: '1.8.9',
    dir: 'client/targets/mc-1.8.9',
    displayName: 'Minecraft 1.8.9',
    minecraftVersion: '1.8.9',
    branch: 'legacy',
    loader: 'legacy-fabric',
    java: { component: 'jre-legacy', majorVersion: 8 },
    description: 'PvP',
    recommended: true,
    tags: ['pvp']
  },
  {
    id: '1.21.11',
    dir: 'client/targets/mc-1.21.11',
    displayName: 'Minecraft 1.21.11',
    minecraftVersion: '1.21.11',
    branch: 'modern',
    loader: 'fabric',
    java: { component: 'java-runtime-delta', majorVersion: 21 },
    description: 'Survival & modern servers',
    tags: ['modern']
  },
  {
    id: '1.21.8',
    dir: 'client/targets/mc-1.21.8',
    displayName: 'Minecraft 1.21.8',
    minecraftVersion: '1.21.8',
    branch: 'modern',
    loader: 'fabric',
    java: { component: 'java-runtime-delta', majorVersion: 21 },
    description: 'Servers still on 1.21.8',
    tags: ['modern']
  },
  {
    id: '26.3',
    dir: 'client/targets/mc-26.3',
    displayName: 'Minecraft 26.3',
    minecraftVersion: '26.3',
    branch: 'modern',
    loader: 'fabric',
    java: { component: 'java-runtime-epsilon', majorVersion: 25 },
    description: 'The newest Minecraft',
    tags: ['modern', 'latest']
  }
];

function gradleProperty(dir, key) {
  const text = readFileSync(join(root, dir, 'gradle.properties'), 'utf8');
  const match = text.match(new RegExp(`^${key}=(.+)$`, 'm'));
  if (!match) {
    throw new Error(`${key} missing in ${dir}/gradle.properties`);
  }
  return match[1].trim();
}

function build(dir) {
  const cwd = join(root, dir);
  console.log(`> Building ${dir}`);
  // .bat files need a shell on Windows; the absolute path is quoted because it may contain spaces
  const result = process.platform === 'win32'
    ? spawnSync(`"${join(cwd, 'gradlew.bat')}" build --console=plain`, { cwd, stdio: 'inherit', shell: true })
    : spawnSync('./gradlew', ['build', '--console=plain'], { cwd, stdio: 'inherit' });
  if (result.status !== 0) {
    throw new Error(`Build failed in ${dir}`);
  }
}

function findJar(dir, version) {
  const libs = join(root, dir, 'build', 'libs');
  // only the jar of the current version (older builds may still be lying around)
  const jars = readdirSync(libs).filter((f) => f.endsWith('.jar') && f.includes(`-${version}+`) && !f.endsWith('-sources.jar') && !f.endsWith('-dev.jar'));
  if (jars.length !== 1) {
    throw new Error(`Expected one jar in ${libs}, found: ${jars.join(', ') || 'none'}`);
  }
  return join(libs, jars[0]);
}

const sha1 = (file) => createHash('sha1').update(readFileSync(file)).digest('hex');

const version = readFileSync(join(root, 'VERSION'), 'utf8').trim();
const launcherVersion = JSON.parse(readFileSync(join(root, 'launcher', 'package.json'), 'utf8')).version;
const out = join(root, 'distribution');
// The previous distribution stays untouched until every target has built: the new one is
// assembled next to it and swapped in at the end.
const staging = join(root, 'distribution.tmp');

if (!skipBuild) {
  for (const target of TARGETS) {
    build(target.dir);
  }
}
const jars = TARGETS.map((target) => findJar(target.dir, version));

rmSync(staging, { recursive: true, force: true });
mkdirSync(staging, { recursive: true });
const targets = [];
for (const [index, target] of TARGETS.entries()) {
  const jar = jars[index];
  const fileName = `medirian-${target.id}-${version}.jar`;
  copyFileSync(jar, join(staging, fileName));
  const artifact = { file: fileName, sha1: sha1(jar), size: statSync(jar).size };
  if (baseUrl) {
    artifact.url = baseUrl + fileName;
  }
  targets.push({
    id: target.id,
    displayName: target.displayName,
    minecraftVersion: target.minecraftVersion,
    branch: target.branch,
    loader: { type: target.loader, version: gradleProperty(target.dir, 'loader_version') },
    java: target.java,
    artifact,
    recommended: target.recommended ?? false,
    tags: target.tags,
    description: target.description
  });
  console.log(`  ${fileName}  ${artifact.sha1}  ${(artifact.size / 1024).toFixed(0)} KB`);
}

const manifest = {
  schema: 1,
  channel: baseUrl ? 'stable' : 'local',
  generatedAt: new Date().toISOString(),
  client: { version },
  launcher: { version: launcherVersion },
  targets
};
writeFileSync(join(staging, 'release-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
rmSync(out, { recursive: true, force: true });
renameSync(staging, out);
console.log(`Wrote ${join(out, 'release-manifest.json')} (${targets.length} targets, Medirian ${version})`);
if (!existsSync(join(out, 'release-manifest.json'))) {
  process.exit(1);
}
