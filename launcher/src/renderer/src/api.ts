import type { EventApi, EventChannel, InvokeApi, InvokeChannel } from '../../common/ipc';

/** Typed access to the main process (see src/common/ipc.ts). */
export function invoke<K extends InvokeChannel>(channel: K, ...args: Parameters<InvokeApi[K]>): Promise<Awaited<ReturnType<InvokeApi[K]>>> {
  return window.meridian.invoke(channel, ...args);
}

export function on<K extends EventChannel>(channel: K, listener: (payload: EventApi[K]) => void): () => void {
  return window.meridian.on(channel, listener);
}

/** Strips Electron's "Error invoking remote method 'x': Error: " prefix from IPC errors. */
export function errorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  return message.replace(/^Error invoking remote method '[^']+': (Error: )?/, '');
}
