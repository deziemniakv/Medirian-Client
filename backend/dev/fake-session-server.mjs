#!/usr/bin/env node
// A stand-in for Mojang's session server, for testing sign-in with offline/development accounts.
// NEVER use it in production: it accepts every join without checking the access token.
//
//   node backend/dev/fake-session-server.mjs [port]          (default 8091)
//   MEDIRIAN_SESSION_SERVER=http://127.0.0.1:8091/session/minecraft node backend/src/index.mjs
//   MEDIRIAN_SERVICES_URL=http://127.0.0.1:8080 MEDIRIAN_SESSION_SERVER=http://127.0.0.1:8091/session/minecraft ./gradlew runClient
import { createServer } from 'node:http';

const port = Number(process.argv[2] ?? 8091);
const joins = new Map();

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'POST' && url.pathname === '/session/minecraft/join') {
    let text = '';
    for await (const chunk of req) text += chunk;
    const { selectedProfile, serverId } = JSON.parse(text || '{}');
    joins.set(serverId, selectedProfile);
    console.log(`join ${selectedProfile} → ${serverId}`);
    res.writeHead(204).end();
  } else if (req.method === 'GET' && url.pathname === '/session/minecraft/hasJoined') {
    const id = joins.get(url.searchParams.get('serverId'));
    if (!id) {
      res.writeHead(204).end();
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ id, name: url.searchParams.get('username'), properties: [] }));
  } else {
    res.writeHead(404).end();
  }
}).listen(port, '127.0.0.1', () => console.log(`Fake session server on http://127.0.0.1:${port}/session/minecraft (testing only)`));
