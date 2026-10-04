#!/usr/bin/env node
// Prints the CHANGELOG.md section of a version (used as the GitHub release description).
//
// Usage: node scripts/release-notes.mjs [version]   (default: the version in VERSION)
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** The body of the "## <version> — …" section, or null when there is none. */
export function releaseNotes(changelog, version) {
  const lines = changelog.split(/\r?\n/);
  const start = lines.findIndex((line) => line.startsWith(`## ${version} `) || line === `## ${version}`);
  if (start < 0) {
    return null;
  }
  let end = lines.findIndex((line, i) => i > start && line.startsWith('## '));
  if (end < 0) {
    end = lines.length;
  }
  const heading = lines[start].replace(/^## /, '');
  const body = lines.slice(start + 1, end).join('\n').trim();
  return { heading, body };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const version = process.argv[2] ?? readFileSync(join(root, 'VERSION'), 'utf8').trim();
  const notes = releaseNotes(readFileSync(join(root, 'CHANGELOG.md'), 'utf8'), version);
  if (!notes) {
    console.error(`CHANGELOG.md has no section for ${version}`);
    process.exit(1);
  }
  console.log(notes.body);
}
