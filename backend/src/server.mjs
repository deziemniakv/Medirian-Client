// Meridian services: accounts (Mojang session handshake), cosmetics and cloud profiles.
//
// Sign-in never sees a Minecraft access token: the client asks for a challenge, "joins" it at
// Mojang's session server with its own token (exactly like joining a Minecraft server) and the
// backend asks Mojang whether that player joined (hasJoined). See docs/SERVICES.md.
import { randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer as createHttpServer } from 'node:http';
import { Store } from './store.mjs';

const CATALOGUE = JSON.parse(readFileSync(new URL('./catalogue.json', import.meta.url), 'utf8')).cosmetics;
const VERSION = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).version;
const COSMETIC_TYPES = ['CAPE', 'WINGS', 'HAT', 'EMOTE', 'TRAIL'];
const PROFILE_NAME = /^[\p{L}\p{N} _-]{1,24}$/u;
const UUID = /^[0-9a-f]{32}$/;
const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/;

export const LIMITS = {
  bodyBytes: 256 * 1024,
  profileBytes: 64 * 1024,
  profiles: 20,
  loadoutBatch: 100,
  challengeMs: 2 * 60 * 1000,
  emoteMs: 10 * 1000,
  sessionMs: 24 * 60 * 60 * 1000,
  requestsPerMinute: 240,
  signInsPerMinute: 12
};

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/** Strips dashes and lowercases a UUID; null when it is not one. */
export function normalizeUuid(value) {
  const uuid = typeof value === 'string' ? value.replace(/-/g, '').toLowerCase() : '';
  return UUID.test(uuid) ? uuid : null;
}

/**
 * @param {object} options
 * @param {string} options.dataDir directory for the JSON documents
 * @param {string} [options.sessionServer] Mojang's session server (overridable for tests)
 * @param {typeof fetch} [options.fetch]
 * @param {() => number} [options.now]
 */
export function createServer({ dataDir, sessionServer = 'https://sessionserver.mojang.com/session/minecraft', fetch: fetchImpl = fetch, now = Date.now }) {
  const store = new Store(dataDir);
  const challenges = new Map();
  const sessions = new Map();
  /** uuid → { emote, startedAt } of emotes being played (kept in memory: they last seconds). */
  const emotes = new Map();
  const rate = new Map();

  function limit(key, perMinute) {
    const minute = Math.floor(now() / 60000);
    const entry = rate.get(key);
    if (!entry || entry.minute !== minute) {
      if (rate.size > 10000) {
        rate.clear();
      }
      rate.set(key, { minute, count: 1 });
      return;
    }
    if (++entry.count > perMinute) {
      throw new HttpError(429, 'Too many requests');
    }
  }

  function prune() {
    const t = now();
    for (const [id, challenge] of challenges) {
      if (challenge.expiresAt < t) challenges.delete(id);
    }
    for (const [token, session] of sessions) {
      if (session.expiresAt < t) sessions.delete(token);
    }
  }

  function authenticate(req) {
    const header = req.headers.authorization ?? '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : '';
    const session = sessions.get(token);
    if (!session || session.expiresAt < now()) {
      throw new HttpError(401, 'Sign in required');
    }
    return session;
  }

  function owned(uuid) {
    const grants = store.grants();
    return CATALOGUE.filter((c) => c.access === 'free' || (grants[c.id] ?? []).map(normalizeUuid).includes(uuid)).map((c) => c.id);
  }

  async function hasJoined(name, serverId) {
    const url = `${sessionServer}/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
    let response;
    try {
      response = await fetchImpl(url, { signal: AbortSignal.timeout(10000) });
    } catch {
      throw new HttpError(502, 'The Mojang session server is unreachable');
    }
    if (response.status === 204) {
      return null;
    }
    if (!response.ok) {
      throw new HttpError(502, `The Mojang session server answered ${response.status}`);
    }
    const profile = await response.json();
    const uuid = normalizeUuid(profile?.id);
    return uuid && typeof profile.name === 'string' ? { uuid, name: profile.name } : null;
  }

  const routes = [
    ['GET', /^\/v1\/status$/, () => ({ name: 'meridian-services', version: VERSION })],

    ['POST', /^\/v1\/auth\/challenge$/, (req) => {
      limit(`signin:${req.socket.remoteAddress}`, LIMITS.signInsPerMinute);
      prune();
      const serverId = randomBytes(20).toString('hex');
      challenges.set(serverId, { expiresAt: now() + LIMITS.challengeMs });
      return { serverId };
    }],

    ['POST', /^\/v1\/auth\/session$/, async (req, body) => {
      limit(`signin:${req.socket.remoteAddress}`, LIMITS.signInsPerMinute);
      const { name, serverId } = body ?? {};
      if (typeof name !== 'string' || !PLAYER_NAME.test(name) || typeof serverId !== 'string') {
        throw new HttpError(400, 'name and serverId are required');
      }
      const challenge = challenges.get(serverId);
      if (!challenge || challenge.expiresAt < now()) {
        throw new HttpError(401, 'Unknown or expired challenge');
      }
      // one attempt per challenge
      challenges.delete(serverId);
      const profile = await hasJoined(name, serverId);
      if (!profile) {
        throw new HttpError(401, 'Mojang did not confirm the session (offline account or wrong challenge)');
      }
      const user = store.user(profile.uuid);
      if (user.name !== profile.name) {
        user.name = profile.name;
        store.save(user);
      }
      const token = randomBytes(32).toString('base64url');
      const expiresAt = now() + LIMITS.sessionMs;
      sessions.set(token, { uuid: profile.uuid, name: profile.name, expiresAt });
      return { token, uuid: profile.uuid, name: profile.name, expiresAt };
    }],

    ['GET', /^\/v1\/me$/, (req) => {
      const session = authenticate(req);
      return { uuid: session.uuid, name: session.name };
    }],

    ['GET', /^\/v1\/cosmetics$/, () => ({ cosmetics: CATALOGUE })],

    ['GET', /^\/v1\/cosmetics\/owned$/, (req) => ({ owned: owned(authenticate(req).uuid) })],

    ['GET', /^\/v1\/cosmetics\/loadout$/, (req) => ({ loadout: store.user(authenticate(req).uuid).loadout })],

    ['PUT', /^\/v1\/cosmetics\/loadout$/, (req, body) => {
      const session = authenticate(req);
      const loadout = {};
      const mine = owned(session.uuid);
      for (const [type, id] of Object.entries(body?.loadout ?? {})) {
        if (!COSMETIC_TYPES.includes(type)) {
          throw new HttpError(400, `Unknown cosmetic type ${type}`);
        }
        if (id === null) {
          continue;
        }
        const cosmetic = CATALOGUE.find((c) => c.id === id);
        if (!cosmetic || cosmetic.type !== type) {
          throw new HttpError(400, `Unknown ${type} ${id}`);
        }
        if (!mine.includes(id)) {
          throw new HttpError(403, `You do not own ${id}`);
        }
        loadout[type] = id;
      }
      const user = store.user(session.uuid);
      user.loadout = loadout;
      store.save(user);
      return { loadout };
    }],

    ['POST', /^\/v1\/cosmetics\/loadouts$/, (req, body) => {
      const players = body?.players;
      if (!Array.isArray(players) || players.length > LIMITS.loadoutBatch) {
        throw new HttpError(400, `players must be an array of at most ${LIMITS.loadoutBatch} UUIDs`);
      }
      const loadouts = {};
      for (const player of players) {
        const uuid = normalizeUuid(player);
        const user = uuid ? store.existing(uuid) : null;
        if (user && Object.keys(user.loadout).length > 0) {
          loadouts[uuid] = user.loadout;
        }
      }
      return { loadouts };
    }],

    ['POST', /^\/v1\/emotes\/play$/, (req, body) => {
      const session = authenticate(req);
      const id = body?.emote;
      const emote = CATALOGUE.find((c) => c.id === id && c.type === 'EMOTE');
      if (!emote) {
        throw new HttpError(400, 'Unknown emote');
      }
      if (!owned(session.uuid).includes(id)) {
        throw new HttpError(403, `You do not own ${id}`);
      }
      const startedAt = now();
      if (emotes.size > 10000) {
        for (const [uuid, playing] of emotes) {
          if (startedAt - playing.startedAt > LIMITS.emoteMs) emotes.delete(uuid);
        }
      }
      emotes.set(session.uuid, { emote: id, startedAt });
      return { emote: id, startedAt };
    }],

    ['POST', /^\/v1\/emotes\/active$/, (req, body) => {
      const players = body?.players;
      if (!Array.isArray(players) || players.length > LIMITS.loadoutBatch) {
        throw new HttpError(400, `players must be an array of at most ${LIMITS.loadoutBatch} UUIDs`);
      }
      const t = now();
      const active = {};
      for (const player of players) {
        const uuid = normalizeUuid(player);
        const playing = uuid ? emotes.get(uuid) : null;
        if (playing && t - playing.startedAt <= LIMITS.emoteMs) {
          // how long ago it started, so clients play it in step whatever their clocks say
          active[uuid] = { emote: playing.emote, elapsedMs: t - playing.startedAt };
        }
      }
      return { emotes: active };
    }],

    ['GET', /^\/v1\/profiles$/, (req) => {
      const user = store.user(authenticate(req).uuid);
      return {
        profiles: Object.entries(user.profiles).map(([name, p]) => ({ name, updatedAt: p.updatedAt, size: JSON.stringify(p.data).length }))
      };
    }],

    ['GET', /^\/v1\/profiles\/([^/]+)$/, (req, _body, [name]) => {
      const profile = store.user(authenticate(req).uuid).profiles[name];
      if (!profile) {
        throw new HttpError(404, 'No such profile');
      }
      return { name, updatedAt: profile.updatedAt, data: profile.data };
    }],

    ['PUT', /^\/v1\/profiles\/([^/]+)$/, (req, body, [name]) => {
      const user = store.user(authenticate(req).uuid);
      if (!PROFILE_NAME.test(name)) {
        throw new HttpError(400, 'Profile names have 1-24 letters, digits, spaces, - or _');
      }
      const data = body?.data;
      if (!data || typeof data !== 'object' || Array.isArray(data)) {
        throw new HttpError(400, 'data must be a JSON object');
      }
      if (JSON.stringify(data).length > LIMITS.profileBytes) {
        throw new HttpError(413, 'Profile too large');
      }
      if (!user.profiles[name] && Object.keys(user.profiles).length >= LIMITS.profiles) {
        throw new HttpError(409, `At most ${LIMITS.profiles} profiles`);
      }
      const updatedAt = now();
      user.profiles[name] = { updatedAt, data };
      store.save(user);
      return { name, updatedAt };
    }],

    ['DELETE', /^\/v1\/profiles\/([^/]+)$/, (req, _body, [name]) => {
      const user = store.user(authenticate(req).uuid);
      if (!user.profiles[name]) {
        throw new HttpError(404, 'No such profile');
      }
      delete user.profiles[name];
      store.save(user);
      return { deleted: name };
    }]
  ];

  async function readBody(req) {
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > LIMITS.bodyBytes) {
        throw new HttpError(413, 'Request too large');
      }
      chunks.push(chunk);
    }
    if (size === 0) {
      return null;
    }
    try {
      return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch {
      throw new HttpError(400, 'Invalid JSON');
    }
  }

  return createHttpServer(async (req, res) => {
    let status = 200;
    let payload;
    try {
      limit(`ip:${req.socket.remoteAddress}`, LIMITS.requestsPerMinute);
      const path = new URL(req.url, 'http://localhost').pathname;
      const route = routes.find(([method, pattern]) => method === req.method && pattern.test(path));
      if (!route) {
        throw new HttpError(routes.some(([, pattern]) => pattern.test(path)) ? 405 : 404, 'Not found');
      }
      const params = route[1].exec(path).slice(1).map(decodeURIComponent);
      const body = req.method === 'GET' ? null : await readBody(req);
      payload = await route[2](req, body, params);
    } catch (error) {
      status = error instanceof HttpError ? error.status : 500;
      payload = { error: error instanceof HttpError ? error.message : 'Internal error' };
      if (status === 500) {
        console.error(error);
      }
    }
    const text = JSON.stringify(payload);
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Content-Length': Buffer.byteLength(text) });
    res.end(text);
  });
}
