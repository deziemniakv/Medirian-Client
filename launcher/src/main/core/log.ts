import { appendFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';

let logFile: string | null = null;
let queue: Promise<void> = Promise.resolve();

/** Configures the launcher log file (launcher/logs/launcher.log). */
export async function initLog(directory: string): Promise<void> {
  await mkdir(directory, { recursive: true });
  logFile = join(directory, 'launcher.log');
}

function write(level: string, message: string, error?: unknown): void {
  const line = `[${new Date().toISOString()}] [${level}] ${message}${error ? ` :: ${error instanceof Error ? error.stack ?? error.message : String(error)}` : ''}`;
  if (level === 'ERROR') {
    console.error(line);
  } else {
    console.log(line);
  }
  if (logFile) {
    const file = logFile;
    queue = queue.then(() => appendFile(file, line + '\n')).catch(() => undefined);
  }
}

export const log = {
  info: (message: string) => write('INFO', message),
  warn: (message: string, error?: unknown) => write('WARN', message, error),
  error: (message: string, error?: unknown) => write('ERROR', message, error)
};
