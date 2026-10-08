// Medirian services: accounts (Mojang session handshake), cosmetics, cloud profiles and profile
// share codes.
//
// Sign-in never sees a Minecraft access token: the client asks for a challenge, "joins" it at
// Mojang's session server with its own token (exactly like joining a Minecraft server) and the
// backend asks Mojang whether that player joined (hasJoined). See docs/SERVICES.md.
import { randomBytes, randomInt } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer as createHttpServer } from 'node:http';
import { Store } from './store.mjs';

const CATALOGUE = JSON.parse(readFileSync(new URL('./catalogue.json', import.meta.url), 'utf8')).cosmetics;
const VERSION = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).version;
const COSMETIC_TYPES = ['CAPE', 'WINGS', 'HAT', 'EMOTE', 'TRAIL'];
const PROFILE_NAME = /^[\p{L}\p{N} _-]{1,24}$/u;
const UUID = /^[0-9a-f]{32}$/;
const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/;
/** Share codes: MDN- and 12 characters without look-alikes (no 0/O, 1/I/L), about 60 bits. */
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const SHARE_CODE = /^MDN-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$/;
/** Keys that never belong in a shared profile, wherever they appear (defence in depth). */
const PRIVATE_KEY = /token|password|secret|credential|session|cookie|auth|private|apikey|api_key/i;

export const LIMITS = {
  bodyBytes: 256 * 1024,
  profileBytes: 64 * 1024,
  profiles: 20,
  loadoutBatch: 100,
  challengeMs: 2 * 60 * 1000,
  emoteMs: 10 * 1000,
  sessionMs: 24 * 60 * 60 * 1000,
  requestsPerMinute: 240,
  signInsPerMinute: 12,
  sharesPerMinute: 6,
  shareMs: 90 * 24 * 60 * 60 * 1000
};

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/** A share code in its canonical form (MDN-XXXX-XXXX-XXXX) from what a player typed, or null. */
export function normalizeShareCode(value) {
  if (typeof value !== 'string') {
    return null;
  }
  const raw = value.toUpperCase().replace(/[\s-]/g, '').replace(/^MDN/, '');
  if (raw.length !== 12) {
    return null;
  }
  const code = `MDN-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8)}`;
  return SHARE_CODE.test(code) ? code : null;
}

function newShareCode() {
  let raw = '';
  for (let i = 0; i < 12; i++) {
    raw += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  }
  return `MDN-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8)}`;
}

/**
 * A copy of a shared profile without anything private: keys that look like credentials are
 * dropped at every level. Throws on a malformed profile.
 */
export function cleanShare(body) {
  const profile = body?.profile;
  if (!profile || typeof profile !== 'object' || Array.isArray(profile) || profile.format !== 'medirian-profile') {
    throw new HttpError(400, 'profile must be a Medirian profile (format "medirian-profile")');
  }
  const scrub = (value, depth) => {
    if (depth > 12) {
      throw new HttpError(400, 'Profile nested too deeply');
    }
    if (Array.isArray(value)) {
      return value.map((v) => scrub(v, depth + 1));
    }
    if (value && typeof value === 'object') {
      const out = {};
      for (const [key, v] of Object.entries(value)) {
        if (!PRIVATE_KEY.test(key)) {
          out[key] = scrub(v, depth + 1);
        }
      }
      return out;
    }
    return value;
  };
  const clean = scrub(profile, 0);
  const name = typeof clean.name === 'string' ? clean.name.trim().slice(0, 40) : '';
  if (!name) {
    throw new HttpError(400, 'profile.name is required');
  }
  clean.name = name;
  if (JSON.stringify(clean).length > LIMITS.profileBytes) {
    throw new HttpError(413, 'Profile too large');
  }
  return clean;
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
 * @param {boolean} [options.trustProxy] behind the HTTPS reverse proxy: client addresses from
 *   X-Forwarded-For, plain-HTTP requests refused, HSTS on every answer
 */
export function createServer({ dataDir, sessionServer = 'https://sessionserver.mojang.com/session/minecraft', fetch: fetchImpl = fetch, now = Date.now, trustProxy = false }) {
  const store = new Store(dataDir);
  const challenges = new Map();
  const sessions = new Map();
  /** uuid → { emote, startedAt } of emotes being played (kept in memory: they last seconds). */
  const emotes = new Map();
  const rate = new Map();

  /** The client's address: the proxy's X-Forwarded-For entry when behind the proxy. */
  function clientIp(req) {
    if (trustProxy) {
      const forwarded = String(req.headers['x-forwarded-for'] ?? '').split(',').map((s) => s.trim()).filter(Boolean);
      if (forwarded.length > 0) {
        return forwarded[forwarded.length - 1];
      }
    }
    return req.socket.remoteAddress ?? 'unknown';
  }

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
    ['GET', /^\/v1\/status$/, () => ({ name: 'medirian-services', version: VERSION })],

    ['POST', /^\/v1\/auth\/challenge$/, (req) => {
      limit(`signin:${clientIp(req)}`, LIMITS.signInsPerMinute);
      prune();
      const serverId = randomBytes(20).toString('hex');
      challenges.set(serverId, { expiresAt: now() + LIMITS.challengeMs });
      return { serverId };
    }],

    ['POST', /^\/v1\/auth\/session$/, async (req, body) => {
      limit(`signin:${clientIp(req)}`, LIMITS.signInsPerMinute);
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

    // ---------------------------------------------------------------- share codes
    // Anyone can turn a profile into a code (no account needed, rate limited) and anyone with the
    // code can read it for 90 days. The creator gets a delete key to withdraw it earlier.

    ['POST', /^\/v1\/shares$/, (req, body) => {
      limit(`share:${clientIp(req)}`, LIMITS.sharesPerMinute);
      const profile = cleanShare(body);
      let code = newShareCode();
      while (store.share(code)) {
        code = newShareCode();
      }
      const createdAt = now();
      const expiresAt = createdAt + LIMITS.shareMs;
      const deleteKey = randomBytes(18).toString('base64url');
      store.saveShare({ code, profile, createdAt, expiresAt, deleteKey });
      return { code, expiresAt, deleteKey };
    }],

    ['GET', /^\/v1\/shares\/([^/]+)$/, (req, _body, [raw]) => {
      const code = normalizeShareCode(raw);
      if (!code) {
        throw new HttpError(400, 'Not a Medirian profile code');
      }
      const share = store.share(code);
      if (!share) {
        throw new HttpError(404, 'No profile with this code (it was deleted or never existed)');
      }
      if (share.expiresAt < now()) {
        store.deleteShare(code);
        throw new HttpError(410, 'This profile code has expired');
      }
      return { code, profile: share.profile, createdAt: share.createdAt, expiresAt: share.expiresAt };
    }],

    ['DELETE', /^\/v1\/shares\/([^/]+)$/, (req, body, [raw]) => {
      const code = normalizeShareCode(raw);
      const share = code ? store.share(code) : null;
      if (!share) {
        throw new HttpError(404, 'No profile with this code');
      }
      if (typeof body?.deleteKey !== 'string' || body.deleteKey !== share.deleteKey) {
        throw new HttpError(403, 'Only the creator can delete this code');
      }
      store.deleteShare(code);
      return { deleted: code };
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
    const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
    if (trustProxy) {
      headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains';
    }
    try {
      // production runs behind the HTTPS proxy only: nothing is answered over plain HTTP
      if (trustProxy && String(req.headers['x-forwarded-proto'] ?? '').split(',')[0].trim() !== 'https') {
        throw new HttpError(403, 'HTTPS required');
      }
      limit(`ip:${clientIp(req)}`, LIMITS.requestsPerMinute);
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
    res.writeHead(status, { ...headers, 'Content-Length': Buffer.byteLength(text) });
    res.end(text);
  });
}
