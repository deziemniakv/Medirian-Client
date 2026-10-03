import { randomUUID } from 'node:crypto';
import { connect, type Socket } from 'node:net';

// Discord's local RPC: a named pipe (Windows) or unix socket per running Discord client.
// Every message is a frame: int32 LE opcode, int32 LE payload length, UTF-8 JSON payload.
// Only Node builtins here (no Electron), so the module is unit-testable with `node --test`.

export const OP = { HANDSHAKE: 0, FRAME: 1, CLOSE: 2, PING: 3, PONG: 4 } as const;

export interface Frame {
  op: number;
  data: Record<string, unknown>;
}

export interface DiscordUser {
  id: string;
  username: string;
  global_name?: string | null;
}

/** Rich presence activity (https://discord.com/developers/docs/rich-presence/using-with-the-game-sdk). */
export interface Activity {
  details?: string;
  state?: string;
  timestamps?: { start?: number; end?: number };
  assets?: { large_image?: string; large_text?: string; small_image?: string; small_text?: string };
  instance?: boolean;
}

export function encodeFrame(op: number, payload: unknown): Buffer {
  const json = Buffer.from(JSON.stringify(payload), 'utf8');
  const frame = Buffer.alloc(8 + json.length);
  frame.writeInt32LE(op, 0);
  frame.writeInt32LE(json.length, 4);
  json.copy(frame, 8);
  return frame;
}

/** Splits received bytes into complete frames; incomplete trailing bytes are returned as {@code rest}. */
export function decodeFrames(buffer: Buffer): { frames: Frame[]; rest: Buffer } {
  const frames: Frame[] = [];
  let offset = 0;
  while (buffer.length - offset >= 8) {
    const op = buffer.readInt32LE(offset);
    const length = buffer.readInt32LE(offset + 4);
    if (length < 0 || length > 1024 * 1024) {
      throw new Error(`Invalid Discord IPC frame length ${length}`);
    }
    if (buffer.length - offset - 8 < length) {
      break;
    }
    const json = buffer.toString('utf8', offset + 8, offset + 8 + length);
    let data: Record<string, unknown> = {};
    try {
      data = JSON.parse(json) as Record<string, unknown>;
    } catch {
      // keep the empty payload: a malformed frame must not break the stream
    }
    frames.push({ op, data });
    offset += 8 + length;
  }
  return { frames, rest: buffer.subarray(offset) };
}

/** Candidate socket paths, in Discord's own order (discord-ipc-0 … 9; Flatpak and Snap on Linux). */
export function ipcPaths(platform: string = process.platform, env: NodeJS.ProcessEnv = process.env): string[] {
  const ids = Array.from({ length: 10 }, (_, i) => i);
  if (platform === 'win32') {
    return ids.map((i) => `\\\\?\\pipe\\discord-ipc-${i}`);
  }
  const base = (env.XDG_RUNTIME_DIR || env.TMPDIR || env.TMP || env.TEMP || '/tmp').replace(/\/+$/, '');
  const dirs = [base, `${base}/app/com.discordapp.Discord`, `${base}/snap.discord`, `${base}/.flatpak/com.discordapp.Discord/xdg-run`];
  return dirs.flatMap((dir) => ids.map((i) => `${dir}/discord-ipc-${i}`));
}

/** Discord is not running (no IPC socket could be opened). */
export class DiscordUnavailableError extends Error {}

interface Pending {
  resolve: (data: Record<string, unknown>) => void;
  reject: (error: Error) => void;
  timer: NodeJS.Timeout;
}

/** One connection to the local Discord client. */
export class DiscordIpcClient {
  private socket: Socket | null = null;
  private buffer: Buffer = Buffer.alloc(0);
  private readonly pending = new Map<string, Pending>();
  private ready: Pending | null = null;
  /** The handshake completed; only then is a close reported to {@link onClose}. */
  private established = false;
  private closeListener: (reason: string) => void = () => undefined;

  get connected(): boolean {
    return this.established;
  }

  /** Called once when an established connection closes (Discord quit, error, rejected client id…). */
  onClose(listener: (reason: string) => void): void {
    this.closeListener = listener;
  }

  /** Opens the first available socket and performs the handshake. */
  async connect(clientId: string, timeoutMs = 5000, paths: string[] = ipcPaths()): Promise<DiscordUser> {
    let socket: Socket | null = null;
    for (const path of paths) {
      socket = await openSocket(path);
      if (socket) {
        break;
      }
    }
    if (!socket) {
      throw new DiscordUnavailableError('Discord is not running');
    }
    this.socket = socket;
    socket.on('data', (chunk: Buffer) => this.receive(chunk));
    socket.on('error', () => socket.destroy());
    socket.on('close', () => this.closed('connection closed'));
    const data = await new Promise<Record<string, unknown>>((resolve, reject) => {
      this.ready = { resolve, reject, timer: setTimeout(() => reject(new Error('Discord did not answer the handshake')), timeoutMs) };
      socket.write(encodeFrame(OP.HANDSHAKE, { v: 1, client_id: clientId }));
    }).finally(() => {
      if (this.ready) {
        clearTimeout(this.ready.timer);
        this.ready = null;
      }
    }).catch((error: Error) => {
      this.close();
      throw error;
    });
    this.established = true;
    const user = (data.data as { user?: DiscordUser } | undefined)?.user;
    return user ?? { id: '', username: 'unknown' };
  }

  /** Sets (or with null clears) this process's activity. */
  setActivity(activity: Activity | null, pid: number = process.pid): Promise<Record<string, unknown>> {
    return this.request('SET_ACTIVITY', { pid, activity });
  }

  /** Closes the connection on purpose: {@link onClose} is not called. */
  close(): void {
    this.established = false;
    const socket = this.socket;
    this.socket = null;
    socket?.destroy();
  }

  private request(cmd: string, args: Record<string, unknown>, timeoutMs = 5000): Promise<Record<string, unknown>> {
    const socket = this.socket;
    if (!socket || !this.established) {
      return Promise.reject(new Error('Not connected to Discord'));
    }
    const nonce = randomUUID();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(nonce);
        reject(new Error(`Discord did not answer ${cmd}`));
      }, timeoutMs);
      this.pending.set(nonce, { resolve, reject, timer });
      socket.write(encodeFrame(OP.FRAME, { cmd, args, nonce }));
    });
  }

  private receive(chunk: Buffer): void {
    let decoded: { frames: Frame[]; rest: Buffer };
    try {
      decoded = decodeFrames(Buffer.concat([this.buffer, chunk]));
    } catch (error) {
      this.socket?.destroy();
      this.closed(error instanceof Error ? error.message : String(error));
      return;
    }
    this.buffer = decoded.rest;
    for (const frame of decoded.frames) {
      this.handle(frame);
    }
  }

  private handle(frame: Frame): void {
    const data = frame.data;
    if (frame.op === OP.PING) {
      this.socket?.write(encodeFrame(OP.PONG, data));
    } else if (frame.op === OP.CLOSE) {
      const reason = `${String(data.message ?? 'closed by Discord')}${data.code !== undefined ? ` (${String(data.code)})` : ''}`;
      this.ready?.reject(new Error(reason));
      this.socket?.destroy();
      this.closed(reason);
    } else if (frame.op === OP.FRAME) {
      if (data.cmd === 'DISPATCH' && data.evt === 'READY') {
        this.ready?.resolve(data);
        return;
      }
      const nonce = typeof data.nonce === 'string' ? data.nonce : null;
      const pending = nonce ? this.pending.get(nonce) : undefined;
      if (pending && nonce) {
        this.pending.delete(nonce);
        clearTimeout(pending.timer);
        if (data.evt === 'ERROR') {
          const error = data.data as { message?: string; code?: number } | undefined;
          pending.reject(new Error(`${error?.message ?? 'Discord error'}${error?.code !== undefined ? ` (${error.code})` : ''}`));
        } else {
          pending.resolve(data);
        }
      }
    }
  }

  private closed(reason: string): void {
    const wasOpen = this.established;
    this.established = false;
    this.socket = null;
    this.buffer = Buffer.alloc(0);
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(new Error(reason));
    }
    this.pending.clear();
    if (wasOpen) {
      const listener = this.closeListener;
      this.closeListener = () => undefined;
      listener(reason);
    }
  }
}

function openSocket(path: string): Promise<Socket | null> {
  return new Promise((resolve) => {
    const socket = connect(path);
    const fail = () => {
      socket.destroy();
      resolve(null);
    };
    socket.once('error', fail);
    socket.once('connect', () => {
      socket.off('error', fail);
      resolve(socket);
    });
  });
}
