import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { selfTestProblems } from '../check-selftest.mjs';
import { releaseNotes } from '../release-notes.mjs';

const CHANGELOG = `# Changelog

Intro.

## 0.2.0 — 2026-11-01 — Next

- New thing.
- Another thing.

## 0.1.0 — 2026-10-03 — Foundations

- First.
`;

test('release notes are the section of the version', () => {
  assert.deepEqual(releaseNotes(CHANGELOG, '0.2.0'), { heading: '0.2.0 — 2026-11-01 — Next', body: '- New thing.\n- Another thing.' });
  assert.equal(releaseNotes(CHANGELOG, '0.1.0').body, '- First.');
  assert.equal(releaseNotes(CHANGELOG, '0.1'), null);
});

test('the current version has release notes', () => {
  const root = new URL('../../', import.meta.url);
  const version = readFileSync(new URL('VERSION', root), 'utf8').trim();
  assert.ok(releaseNotes(readFileSync(new URL('CHANGELOG.md', root), 'utf8'), version), `CHANGELOG.md needs a ## ${version} section`);
});

test('self-test log check', () => {
  const ok = '[x] Mixin audit passed\n[x] Self-test: chat OK\n[x] Self-test finished; screenshots in run';
  assert.deepEqual(selfTestProblems(ok), []);
  assert.deepEqual(selfTestProblems(ok + '\n[x] Self-test FAILED: chat copy'), ['[x] Self-test FAILED: chat copy']);
  assert.equal(selfTestProblems('[x] Mixin audit passed').length, 1);
  assert.equal(selfTestProblems('').length, 2);
});

test('the client ships the same cosmetics catalogue as the services', () => {
  const root = new URL('../../', import.meta.url);
  const client = readFileSync(new URL('client/shared/src/main/resources/medirian/cosmetics/catalogue.json', root), 'utf8');
  const services = readFileSync(new URL('backend/src/catalogue.json', root), 'utf8');
  assert.deepEqual(JSON.parse(client), JSON.parse(services));
  for (const cosmetic of JSON.parse(client).cosmetics.filter((c) => c.type === 'CAPE')) {
    readFileSync(new URL(`client/shared/src/main/resources/assets/medirian/textures/cosmetics/capes/${cosmetic.id}.png`, root));
  }
});
