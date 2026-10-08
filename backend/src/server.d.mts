// Types of server.mjs for TypeScript callers (the launcher's tests start the real services in-process).
import type { Server } from 'node:http';

export interface ServerOptions {
  /** Directory for the JSON documents. */
  dataDir: string;
  /** Mojang's session server (overridable for tests). */
  sessionServer?: string;
  fetch?: typeof fetch;
  now?: () => number;
  /** Behind the HTTPS reverse proxy: X-Forwarded-For, plain HTTP refused, HSTS. */
  trustProxy?: boolean;
}

export declare const LIMITS: Readonly<Record<string, number>>;
export declare function createServer(options: ServerOptions): Server;
export declare function normalizeUuid(value: unknown): string | null;
export declare function normalizeShareCode(value: unknown): string | null;
