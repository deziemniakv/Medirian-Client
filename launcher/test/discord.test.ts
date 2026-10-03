import assert from 'node:assert/strict';
import { createServer, type Server, type Socket } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import type { GameState } from '../src/common/types.ts';
import { decodeFrames, DiscordIpcClient, DiscordUnavailableError, encodeFrame, OP, type DiscordUser, type Frame } from '../src/main/discord/ipc.ts';
import { buildActivity, DiscordPresenceService, type PresenceContext } from '../src/main/discord/presence.ts';

let pipeCounter = 0;

function pipePath(): string {
  const name = `meridian-discord-test-${process.pid}-${pipeCounter++}`;
  return process.platform === 'win32' ? `\\\\?\\pipe\\${name}` : join(tmpdir(), `${name}.sock`);
}

/** A fake Discord client: answers frames with {@code reply}. */
async function fakeDiscord(reply: (frame: Frame, socket: Socket) => void): Promise<{ path: string; server: Server; received: Frame[] }> {
  const path = pipePath();
  const received: Frame[] = [];
  const server = createServer((socket) => {
    let buffer: Buffer = Buffer.alloc(0);
    socket.on('data', (chunk: Buffer) => {
      const decoded = decodeFrames(Buffer.concat([buffer, chunk]));
      buffer = decoded.rest;
      for (const frame of decoded.frames) {
        received.push(frame);
        reply(frame, socket);
      }
    });
    socket.on('error', () => undefined);
  });
  await new Promise<void>((resolve) => server.listen(path, resolve));
  return { path, server, received };
}

const READY = { cmd: 'DISPATCH', evt: 'READY', data: { v: 1, user: { id: '42', username: 'steve', global_name: 'Steve' } } };

test('frames round-trip and split across chunks', () => {
  const a = encodeFrame(OP.FRAME, { cmd: 'X', nonce: 'ą' });
  const b = encodeFrame(OP.PING, { n: 1 });
  const all = Buffer.concat([a, b]);
  const first = decodeFrames(all.subarray(0, a.length + 3));
  assert.equal(first.frames.length, 1);
  assert.deepEqual(first.frames[0], { op: OP.FRAME, data: { cmd: 'X', nonce: 'ą' } });
  const second = decodeFrames(Buffer.concat([first.rest, all.subarray(a.length + 3)]));
  assert.deepEqual(second.frames, [{ op: OP.PING, data: { n: 1 } }]);
  assert.equal(second.rest.length, 0);
});

test('handshake, activity updates and pings', async () => {
  const fake = await fakeDiscord((frame, socket) => {
    if (frame.op === OP.HANDSHAKE) {
      assert.deepEqual(frame.data, { v: 1, client_id: '1234' });
      socket.write(encodeFrame(OP.FRAME, READY));
      socket.write(encodeFrame(OP.PING, { ping: true }));
    } else if (frame.op === OP.FRAME && frame.data.cmd === 'SET_ACTIVITY') {
      socket.write(encodeFrame(OP.FRAME, { cmd: 'SET_ACTIVITY', nonce: frame.data.nonce, evt: null, data: {} }));
    }
  });
  const client = new DiscordIpcClient();
  try {
    const user = await client.connect('1234', 2000, [fake.path]);
    assert.equal(user.global_name, 'Steve');
    assert.ok(client.connected);
    await client.setActivity({ details: 'Minecraft 1.8.9', state: 'In the menus' }, 777);
    const set = fake.received.find((frame) => frame.data.cmd === 'SET_ACTIVITY');
    assert.deepEqual((set?.data.args as Record<string, unknown>).pid, 777);
    assert.ok(fake.received.some((frame) => frame.op === OP.PONG), 'pings are answered');
  } finally {
    client.close();
    fake.server.close();
  }
});

test('errors from Discord reject the request', async () => {
  const fake = await fakeDiscord((frame, socket) => {
    if (frame.op === OP.HANDSHAKE) {
      socket.write(encodeFrame(OP.FRAME, READY));
    } else if (frame.op === OP.FRAME) {
      socket.write(encodeFrame(OP.FRAME, { cmd: frame.data.cmd, nonce: frame.data.nonce, evt: 'ERROR', data: { code: 4000, message: 'child "activity" fails' } }));
    }
  });
  const client = new DiscordIpcClient();
  try {
    await client.connect('1234', 2000, [fake.path]);
    await assert.rejects(client.setActivity({ details: 'x' }), /activity.*\(4000\)/);
  } finally {
    client.close();
    fake.server.close();
  }
});

test('a rejected application id fails the handshake without a close callback', async () => {
  const fake = await fakeDiscord((frame, socket) => {
    if (frame.op === OP.HANDSHAKE) {
      socket.write(encodeFrame(OP.CLOSE, { code: 4000, message: 'Invalid Client ID' }));
    }
  });
  const client = new DiscordIpcClient();
  let closed = false;
  client.onClose(() => (closed = true));
  try {
    await assert.rejects(client.connect('bad', 2000, [fake.path]), /Invalid Client ID \(4000\)/);
    assert.equal(client.connected, false);
    assert.equal(closed, false);
  } finally {
    fake.server.close();
  }
});

test('no Discord running', async () => {
  await assert.rejects(new DiscordIpcClient().connect('1234', 2000, [pipePath()]), DiscordUnavailableError);
});

function context(game: GameState, patch: Partial<PresenceContext> = {}): PresenceContext {
  return {
    game,
    targetName: (id) => (id === '1.8.9' ? 'Minecraft 1.8.9' : null),
    profileTarget: () => '1.8.9',
    version: '0.1.2',
    language: 'en',
    showServer: true,
    showInLauncher: false,
    ...patch
  };
}

const running = (bridge: Extract<GameState, { state: 'running' }>['bridge']): GameState =>
  ({ state: 'running', profileId: 'p', targetId: '1.8.9', pid: 1, since: 1_700_000_000_000, bridge });

test('activity while playing', () => {
  const multiplayer = running({ connected: true, state: 'multiplayer', server: 'MC.Example.net', profile: 'PvP', clientVersion: '0.1.2' });
  const activity = buildActivity(context(multiplayer));
  assert.equal(activity?.details, 'Minecraft 1.8.9 · PvP');
  assert.equal(activity?.state, 'Playing on mc.example.net');
  assert.equal(activity?.timestamps?.start, 1_700_000_000_000);
  assert.equal(activity?.assets?.large_image, 'meridian');
  assert.equal(activity?.assets?.large_text, 'Meridian Client 0.1.2');

  assert.equal(buildActivity(context(multiplayer, { showServer: false }))?.state, 'Multiplayer');
  assert.equal(buildActivity(context(multiplayer, { language: 'pl' }))?.state, 'Gra na mc.example.net');
  assert.equal(buildActivity(context(running({ connected: true, state: 'menu' })))?.state, 'In the menus');
  assert.equal(buildActivity(context(running({ connected: false })))?.state, 'Loading…');

  const long = buildActivity(context(running({ connected: true, state: 'multiplayer', server: 'a'.repeat(300) })));
  assert.ok((long?.state?.length ?? 0) <= 128);
});

test('activity outside the game', () => {
  const preparing: GameState = { state: 'preparing', profileId: 'p', progress: { phase: 'assets', label: '', done: 0, total: 1 } };
  assert.equal(buildActivity(context(preparing))?.details, 'Starting Minecraft 1.8.9');
  assert.equal(buildActivity(context({ state: 'idle' })), null);
  assert.equal(buildActivity(context({ state: 'idle' }, { showInLauncher: true }))?.details, 'In the launcher');
});

async function waitFor(condition: () => boolean, timeoutMs = 3000): Promise<void> {
  const start = Date.now();
  while (!condition()) {
    if (Date.now() - start > timeoutMs) {
      throw new Error('timed out');
    }
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
}

test('presence service connects and sends the current activity', async () => {
  const fake = await fakeDiscord((frame, socket) => {
    if (frame.op === OP.HANDSHAKE) {
      socket.write(encodeFrame(OP.FRAME, READY));
    } else if (frame.op === OP.FRAME) {
      socket.write(encodeFrame(OP.FRAME, { cmd: frame.data.cmd, nonce: frame.data.nonce, evt: null, data: {} }));
    }
  });
  class FakeClient extends DiscordIpcClient {
    override connect(clientId: string): Promise<DiscordUser> {
      return super.connect(clientId, 2000, [fake.path]);
    }
  }
  const service = new DiscordPresenceService({
    enabled: () => true,
    applicationId: () => '1234',
    activity: () => ({ details: 'Minecraft 1.21.11', state: 'In the menus' }),
    emitStatus: () => undefined,
    log: { info: () => undefined, warn: () => undefined },
    createClient: () => new FakeClient()
  });
  try {
    service.refresh();
    await waitFor(() => fake.received.some((frame) => frame.data.cmd === 'SET_ACTIVITY'));
    const set = fake.received.find((frame) => frame.data.cmd === 'SET_ACTIVITY');
    assert.deepEqual((set?.data.args as { activity: unknown }).activity, { details: 'Minecraft 1.21.11', state: 'In the menus' });
    assert.deepEqual(service.current(), { state: 'connected', user: 'Steve' });
  } finally {
    service.dispose();
    fake.server.close();
  }
});
