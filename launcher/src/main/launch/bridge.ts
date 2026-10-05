import { randomBytes } from 'node:crypto';
import { createServer, type Server, type Socket } from 'node:net';
import type { BridgeStatus } from '../../common/types';
import { log } from '../core/log';

/**
 * Local channel to the running Medirian client (protocol: docs/PROTOCOL.md).
 * Listens on 127.0.0.1 with a random port; the client must authenticate with a one-time token
 * in its first message.
 */
export class ClientBridge {
  private server: Server | null = null;
  private socket: Socket | null = null;
  private status: BridgeStatus = { connected: false };
  readonly token = randomBytes(16).toString('hex');
  port = 0;

  constructor(private readonly onStatus: (status: BridgeStatus) => void) {}

  async start(): Promise<void> {
    this.server = createServer((socket) => this.accept(socket));
    await new Promise<void>((resolve, reject) => {
      this.server!.once('error', reject);
      this.server!.listen(0, '127.0.0.1', () => resolve());
    });
    const address = this.server.address();
    this.port = typeof address === 'object' && address ? address.port : 0;
  }

  private accept(socket: Socket): void {
    let authenticated = false;
    let buffer = '';
    socket.setEncoding('utf8');
    const timeout = setTimeout(() => {
      if (!authenticated) {
        socket.destroy();
      }
    }, 10_000);
    socket.on('data', (chunk: string) => {
      buffer += chunk;
      if (buffer.length > 64 * 1024) {
        socket.destroy();
        return;
      }
      let newline: number;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline).trim();
        buffer = buffer.slice(newline + 1);
        if (!line) {
          continue;
        }
        let message: Record<string, unknown>;
        try {
          message = JSON.parse(line);
        } catch {
          continue;
        }
        if (!authenticated) {
          if (message.type !== 'hello' || message.token !== this.token) {
            log.warn('Rejected client bridge connection with invalid token');
            socket.destroy();
            return;
          }
          authenticated = true;
          clearTimeout(timeout);
          this.socket?.destroy();
          this.socket = socket;
          this.update({ connected: true, clientVersion: String(message.clientVersion ?? ''), profile: String(message.profile ?? '') });
          continue;
        }
        this.handle(message);
      }
    });
    socket.on('close', () => {
      clearTimeout(timeout);
      if (this.socket === socket) {
        this.socket = null;
        this.update({ ...this.status, connected: false });
      }
    });
    socket.on('error', () => socket.destroy());
  }

  private handle(message: Record<string, unknown>): void {
    if (message.type === 'status') {
      const state = message.state;
      this.update({
        ...this.status,
        state: state === 'menu' || state === 'singleplayer' || state === 'multiplayer' ? state : undefined,
        server: typeof message.server === 'string' ? message.server : undefined
      });
    } else if (message.type === 'profile' && typeof message.name === 'string') {
      this.update({ ...this.status, profile: message.name });
    }
  }

  private update(status: BridgeStatus): void {
    this.status = status;
    this.onStatus(status);
  }

  /** Shows a Medirian notification inside the game. */
  notify(title: string, message: string, level: 'info' | 'success' | 'warning' = 'info'): void {
    this.socket?.write(JSON.stringify({ type: 'notify', title, message, level }) + '\n');
  }

  close(): void {
    this.socket?.destroy();
    this.server?.close();
    this.server = null;
  }
}
