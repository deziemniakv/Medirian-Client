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
  dir = mkdtempSync(join(tmpdir(), 'medirian-services-'));
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
  assert.equal(body.name, 'medirian-services');
  // the website reads it from another origin; nothing else is open to other origins
  assert.equal((await fetch(`${base}/v1/status`)).headers.get('access-control-allow-origin'), '*');
  assert.equal((await fetch(`${base}/v1/cosmetics`)).headers.get('access-control-allow-origin'), null);
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

// ---------------------------------------------------------------- profile share codes

const SHARED = {
  format: 'medirian-profile', version: 1, name: 'My PvP',
  profile: { name: 'PvP', settings: { hudScale: 0.8 }, modules: { cps: { enabled: true, settings: { color: '#FF9B55D6' } } } },
  launch: { targetId: '1.8.9', memoryMb: 3072 },
  // nothing like this may survive, at any depth
  accessToken: 'leak', extra: { sessionId: 'leak', apiKey: 'leak', keep: 1 }
};

test('a profile becomes a code that anyone can import, without anything private', async () => {
  const created = await call('POST', '/v1/shares', { body: { profile: SHARED } });
  assert.equal(created.status, 200, JSON.stringify(created.body));
  assert.match(created.body.code, /^MDN-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$/);
  assert.equal(typeof created.body.deleteKey, 'string');
  // typed sloppily: lower case, spaces, no prefix
  const typed = created.body.code.slice(4).toLowerCase().replace(/-/g, ' ');
  const read = await call('GET', `/v1/shares/${encodeURIComponent(typed)}`);
  assert.equal(read.status, 200);
  assert.equal(read.body.code, created.body.code);
  assert.deepEqual(read.body.profile.profile, SHARED.profile);
  assert.equal(read.body.profile.accessToken, undefined);
  assert.deepEqual(read.body.profile.extra, { keep: 1 });
  assert.equal(JSON.stringify(read.body).includes('leak'), false);
  assert.equal(read.body.deleteKey, undefined, 'the delete key stays with the creator');
});

test('invalid, unknown, expired and deleted codes are told apart', async () => {
  assert.equal((await call('GET', '/v1/shares/hello')).status, 400);
  assert.equal((await call('GET', '/v1/shares/MDN-2222-3333-4444')).status, 404);
  const created = (await call('POST', '/v1/shares', { body: { profile: SHARED } })).body;
  assert.equal((await call('DELETE', `/v1/shares/${created.code}`, { body: { deleteKey: 'wrong' } })).status, 403);
  assert.equal((await call('DELETE', `/v1/shares/${created.code}`, { body: { deleteKey: created.deleteKey } })).status, 200);
  assert.equal((await call('GET', `/v1/shares/${created.code}`)).status, 404);
  const old = (await call('POST', '/v1/shares', { body: { profile: SHARED } })).body;
  clock += LIMITS.shareMs + 1;
  const expired = await call('GET', `/v1/shares/${old.code}`);
  assert.equal(expired.status, 410);
  assert.match(expired.body.error, /expired/);
});

test('only Medirian profiles can be shared, and not too often', async () => {
  assert.equal((await call('POST', '/v1/shares', { body: { profile: { name: 'x' } } })).status, 400);
  assert.equal((await call('POST', '/v1/shares', { body: { profile: { format: 'medirian-profile', name: 'x', blob: 'a'.repeat(70000) } } })).status, 413);
  clock += 60_000;
  let status = 200;
  for (let i = 0; i <= LIMITS.sharesPerMinute && status === 200; i++) {
    status = (await call('POST', '/v1/shares', { body: { profile: SHARED } })).status;
  }
  assert.equal(status, 429);
});

// ---------------------------------------------------------------- production behind the HTTPS proxy

test('behind the proxy only HTTPS requests are answered, with HSTS and per-client limits', async () => {
  const proxied = createServer({ dataDir: dir, fetch: fakeMojang, now: () => clock, trustProxy: true });
  await new Promise((resolve) => proxied.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${proxied.address().port}/v1/status`;
  try {
    const plain = await fetch(url, { headers: { 'X-Forwarded-Proto': 'http', 'X-Forwarded-For': '203.0.113.7' } });
    assert.equal(plain.status, 403);
    const secure = await fetch(url, { headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-For': '203.0.113.7' } });
    assert.equal(secure.status, 200);
    assert.match(secure.headers.get('strict-transport-security'), /max-age=31536000/);
    // the limit counts the real client from X-Forwarded-For, not the proxy
    clock += 60_000;
    let status = 200;
    for (let i = 0; i < LIMITS.requestsPerMinute && status === 200; i++) {
      status = (await fetch(url, { headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-For': '203.0.113.8' } })).status;
    }
    assert.equal(status, 200);
    assert.equal((await fetch(url, { headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-For': '203.0.113.8' } })).status, 429);
    assert.equal((await fetch(url, { headers: { 'X-Forwarded-Proto': 'https', 'X-Forwarded-For': '203.0.113.9' } })).status, 200);
  } finally {
    proxied.close();
  }
});
