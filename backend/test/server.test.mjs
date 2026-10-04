import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';
import { createServer, LIMITS, normalizeUuid } from '../src/server.mjs';

const ALEX = { id: '0f1e2d3c4b5a69788796a5b4c3d2e1f0', name: 'Alex' };
const STEVE = { id: '11112222333344445555666677778888', name: 'Steve' };

let dir;
let server;
let base;
let clock = 1_000_000;
/** serverId → profile, filled when a test "joins" at the fake Mojang session server. */
const joined = new Map();

const fakeMojang = async (url) => {
  const query = new URL(url).searchParams;
  const profile = joined.get(query.get('serverId'));
  if (!profile || profile.name !== query.get('username')) {
    return new Response(null, { status: 204 });
  }
  return Response.json({ id: profile.id, name: profile.name, properties: [] });
};

before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'meridian-services-'));
  server = createServer({ dataDir: dir, fetch: fakeMojang, now: () => clock });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => {
  server.close();
  rmSync(dir, { recursive: true, force: true });
});

async function call(method, path, { body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: response.status, body: await response.json() };
}

/** The whole handshake: challenge, join at "Mojang", session. */
async function signIn(profile) {
  const { body: challenge } = await call('POST', '/v1/auth/challenge');
  joined.set(challenge.serverId, profile);
  const { status, body } = await call('POST', '/v1/auth/session', { body: { name: profile.name, serverId: challenge.serverId } });
  assert.equal(status, 200, JSON.stringify(body));
  return body.token;
}

test('status', async () => {
  const { status, body } = await call('GET', '/v1/status');
  assert.equal(status, 200);
  assert.equal(body.name, 'meridian-services');
});

test('sign-in works only after joining the challenge at the session server', async () => {
  const { body: challenge } = await call('POST', '/v1/auth/challenge');
  assert.match(challenge.serverId, /^[0-9a-f]{40}$/);
  // not joined: Mojang answers 204
  const refused = await call('POST', '/v1/auth/session', { body: { name: 'Alex', serverId: challenge.serverId } });
  assert.equal(refused.status, 401);
  // a challenge is single-use
  joined.set(challenge.serverId, ALEX);
  assert.equal((await call('POST', '/v1/auth/session', { body: { name: 'Alex', serverId: challenge.serverId } })).status, 401);
  // unknown challenge
  assert.equal((await call('POST', '/v1/auth/session', { body: { name: 'Alex', serverId: 'f'.repeat(40) } })).status, 401);

  const token = await signIn(ALEX);
  const me = await call('GET', '/v1/me', { token });
  assert.deepEqual(me.body, { uuid: ALEX.id, name: 'Alex' });
  assert.equal((await call('GET', '/v1/me', { token: 'nope' })).status, 401);
});

test('expired challenges and sessions are refused', async () => {
  const { body: challenge } = await call('POST', '/v1/auth/challenge');
  joined.set(challenge.serverId, ALEX);
  clock += LIMITS.challengeMs + 1;
  assert.equal((await call('POST', '/v1/auth/session', { body: { name: 'Alex', serverId: challenge.serverId } })).status, 401);
  const token = await signIn(ALEX);
  clock += LIMITS.sessionMs + 1;
  assert.equal((await call('GET', '/v1/me', { token })).status, 401);
});

test('loadouts: own cosmetics only, visible to everyone', async () => {
  const token = await signIn(ALEX);
  const catalogue = (await call('GET', '/v1/cosmetics')).body.cosmetics;
  assert.ok(catalogue.some((c) => c.id === 'cape_moonlit'));
  const owned = (await call('GET', '/v1/cosmetics/owned', { token })).body.owned;
  assert.ok(owned.includes('cape_moonlit'));
  assert.ok(!owned.includes('cape_founder'), 'granted cosmetics are not free');

  assert.equal((await call('PUT', '/v1/cosmetics/loadout', { token, body: { loadout: { CAPE: 'cape_founder' } } })).status, 403);
  assert.equal((await call('PUT', '/v1/cosmetics/loadout', { token, body: { loadout: { CAPE: 'cape_nope' } } })).status, 400);
  assert.equal((await call('PUT', '/v1/cosmetics/loadout', { token, body: { loadout: { HAT: 'cape_moonlit' } } })).status, 400);
  const saved = await call('PUT', '/v1/cosmetics/loadout', { token, body: { loadout: { CAPE: 'cape_moonlit' } } });
  assert.deepEqual(saved.body.loadout, { CAPE: 'cape_moonlit' });
  assert.deepEqual((await call('GET', '/v1/cosmetics/loadout', { token })).body.loadout, { CAPE: 'cape_moonlit' });

  // anybody can look up loadouts, with or without dashes; players without one are left out
  const dashed = `${ALEX.id.slice(0, 8)}-${ALEX.id.slice(8, 12)}-${ALEX.id.slice(12, 16)}-${ALEX.id.slice(16, 20)}-${ALEX.id.slice(20)}`;
  const lookup = await call('POST', '/v1/cosmetics/loadouts', { body: { players: [dashed, STEVE.id, 'garbage'] } });
  assert.deepEqual(lookup.body.loadouts, { [ALEX.id]: { CAPE: 'cape_moonlit' } });
  assert.equal((await call('POST', '/v1/cosmetics/loadouts', { body: { players: new Array(LIMITS.loadoutBatch + 1).fill(STEVE.id) } })).status, 400);

  // unequip
  const cleared = await call('PUT', '/v1/cosmetics/loadout', { token, body: { loadout: { CAPE: null } } });
  assert.deepEqual(cleared.body.loadout, {});
});

test('granted cosmetics', async () => {
  writeFileSync(join(dir, 'grants.json'), JSON.stringify({ cape_founder: [STEVE.id] }));
  const token = await signIn(STEVE);
  assert.ok((await call('GET', '/v1/cosmetics/owned', { token })).body.owned.includes('cape_founder'));
  assert.equal((await call('PUT', '/v1/cosmetics/loadout', { token, body: { loadout: { CAPE: 'cape_founder' } } })).status, 200);
});

test('cloud profiles', async () => {
  const token = await signIn(ALEX);
  assert.equal((await call('GET', '/v1/profiles')).status, 401);
  const data = { version: 1, modules: { fps: { enabled: true } } };
  const put = await call('PUT', '/v1/profiles/PvP%20Main', { token, body: { data } });
  assert.equal(put.status, 200);
  assert.equal(put.body.name, 'PvP Main');
  const list = (await call('GET', '/v1/profiles', { token })).body.profiles;
  assert.deepEqual(list.map((p) => p.name), ['PvP Main']);
  assert.deepEqual((await call('GET', '/v1/profiles/PvP%20Main', { token })).body.data, data);
  // another player does not see it
  const other = await signIn(STEVE);
  assert.equal((await call('GET', '/v1/profiles/PvP%20Main', { token: other })).status, 404);

  assert.equal((await call('PUT', '/v1/profiles/bad%2Fname', { token, body: { data } })).status, 400);
  assert.equal((await call('PUT', '/v1/profiles/a%3Fb', { token, body: { data } })).status, 400);
  assert.equal((await call('PUT', '/v1/profiles/Big', { token, body: { data: { blob: 'x'.repeat(LIMITS.profileBytes) } } })).status, 413);
  assert.equal((await call('PUT', '/v1/profiles/List', { token, body: { data: [] } })).status, 400);
  assert.equal((await call('DELETE', '/v1/profiles/PvP%20Main', { token })).status, 200);
  assert.equal((await call('GET', '/v1/profiles/PvP%20Main', { token })).status, 404);
});

test('profiles survive a restart (stored on disk)', async () => {
  const token = await signIn(ALEX);
  await call('PUT', '/v1/profiles/Kept', { token, body: { data: { a: 1 } } });
  const restarted = createServer({ dataDir: dir, fetch: fakeMojang, now: () => clock });
  await new Promise((resolve) => restarted.listen(0, '127.0.0.1', resolve));
  try {
    const port = restarted.address().port;
    const { body: challenge } = await (await fetch(`http://127.0.0.1:${port}/v1/auth/challenge`, { method: 'POST' })).json().then((b) => ({ body: b }));
    joined.set(challenge.serverId, ALEX);
    const session = await (await fetch(`http://127.0.0.1:${port}/v1/auth/session`, {
      method: 'POST', body: JSON.stringify({ name: 'Alex', serverId: challenge.serverId })
    })).json();
    const profile = await (await fetch(`http://127.0.0.1:${port}/v1/profiles/Kept`, { headers: { Authorization: `Bearer ${session.token}` } })).json();
    assert.deepEqual(profile.data, { a: 1 });
  } finally {
    restarted.close();
  }
});

test('bad requests', async () => {
  assert.equal((await call('GET', '/v1/nothing')).status, 404);
  assert.equal((await call('DELETE', '/v1/status')).status, 405);
  const response = await fetch(`${base}/v1/auth/session`, { method: 'POST', body: '{not json' });
  assert.equal(response.status, 400);
  assert.equal((await call('POST', '/v1/auth/session', { body: { name: 'bad name!', serverId: 'x' } })).status, 400);
});

test('uuid normalisation', () => {
  assert.equal(normalizeUuid('0F1E2D3C-4B5A-6978-8796-A5B4C3D2E1F0'), ALEX.id);
  assert.equal(normalizeUuid('nope'), null);
  assert.equal(normalizeUuid(42), null);
});

test('emotes are shared for ten seconds', async () => {
  clock += 60_000; // a new minute for the sign-in rate limit
  const token = await signIn(ALEX);
  assert.equal((await call('POST', '/v1/emotes/play', { body: { emote: 'emote_wave' } })).status, 401);
  assert.equal((await call('POST', '/v1/emotes/play', { token, body: { emote: 'cape_moonlit' } })).status, 400);
  assert.equal((await call('POST', '/v1/emotes/play', { token, body: { emote: 'emote_wave' } })).status, 200);
  clock += 1500;
  const active = await call('POST', '/v1/emotes/active', { body: { players: [ALEX.id, STEVE.id] } });
  assert.deepEqual(active.body.emotes, { [ALEX.id]: { emote: 'emote_wave', elapsedMs: 1500 } });
  clock += LIMITS.emoteMs;
  assert.deepEqual((await call('POST', '/v1/emotes/active', { body: { players: [ALEX.id] } })).body.emotes, {});
});
