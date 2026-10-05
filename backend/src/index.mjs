#!/usr/bin/env node
// Starts Medirian services.
//   PORT            (default 8080)
//   HOST            (default 0.0.0.0)
//   DATA_DIR        (default ./data)
//   MOJANG_SESSION_URL  only for local testing with a fake session server
import { resolve } from 'node:path';
import { createServer } from './server.mjs';

const port = Number(process.env.PORT ?? 8080);
const host = process.env.HOST ?? '0.0.0.0';
const dataDir = resolve(process.env.DATA_DIR ?? 'data');
const options = { dataDir };
if (process.env.MOJANG_SESSION_URL) {
  options.sessionServer = process.env.MOJANG_SESSION_URL.replace(/\/$/, '');
  console.warn(`Using the session server at ${options.sessionServer} instead of Mojang's (testing only)`);
}
createServer(options).listen(port, host, () => {
  console.log(`Medirian services listening on http://${host}:${port} (data: ${dataDir})`);
});
