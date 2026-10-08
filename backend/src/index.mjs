#!/usr/bin/env node
// Starts Medirian Services. Configuration comes from the repository's .env (npm start loads it) or
// the environment (Docker, systemd); see .env.example and docs/OWNER_SETUP.md:
//   MEDIRIAN_SERVICES_PORT         port (default 8080; PORT works too, as hosting platforms set it)
//   MEDIRIAN_SERVICES_HOST         interface (default 127.0.0.1 — only the reverse proxy on this machine)
//   MEDIRIAN_SERVICES_DATA_DIR     where the JSON documents live (default ./data)
//   MEDIRIAN_SERVICES_TRUST_PROXY  1 behind the HTTPS reverse proxy (Caddy): client addresses come from
//                                  X-Forwarded-For and requests that did not arrive over HTTPS are refused
//   MEDIRIAN_SESSION_SERVER        testing only: a fake Mojang session server
import { resolve } from 'node:path';
import { createServer } from './server.mjs';

const env = process.env;
const port = Number(env.MEDIRIAN_SERVICES_PORT || env.PORT || 8080);
const host = env.MEDIRIAN_SERVICES_HOST || '127.0.0.1';
const dataDir = resolve(env.MEDIRIAN_SERVICES_DATA_DIR || 'data');
const trustProxy = /^(1|true|yes)$/i.test(env.MEDIRIAN_SERVICES_TRUST_PROXY ?? '');
const options = { dataDir, trustProxy };
if (env.MEDIRIAN_SESSION_SERVER) {
  options.sessionServer = env.MEDIRIAN_SESSION_SERVER.replace(/\/$/, '');
  console.warn(`Using the session server at ${options.sessionServer} instead of Mojang's (testing only)`);
}
if (!trustProxy && host !== '127.0.0.1' && host !== 'localhost') {
  console.warn('Medirian Services is reachable without the HTTPS reverse proxy. In production set MEDIRIAN_SERVICES_TRUST_PROXY=1 and put Caddy in front (backend/deploy).');
}
createServer(options).listen(port, host, () => {
  console.log(`Medirian Services listening on http://${host}:${port} (data: ${dataDir}${trustProxy ? ', behind an HTTPS proxy' : ''})`);
});
