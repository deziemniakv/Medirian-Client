import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron';
import { EVENT_CHANNELS, INVOKE_CHANNELS, type MedirianBridge } from '../common/ipc';

const invokeAllowed = new Set<string>(INVOKE_CHANNELS);
const eventsAllowed = new Set<string>(EVENT_CHANNELS);

// Narrow, typed API: the renderer can only use the channels declared in common/ipc.ts.
const bridge: MedirianBridge = {
  invoke(channel, ...args) {
    if (!invokeAllowed.has(channel)) {
      return Promise.reject(new Error(`Channel not allowed: ${channel}`));
    }
    return ipcRenderer.invoke(channel, ...args);
  },
  on(channel, listener) {
    if (!eventsAllowed.has(channel)) {
      throw new Error(`Event not allowed: ${channel}`);
    }
    const handler = (_event: IpcRendererEvent, payload: unknown) => listener(payload as never);
    ipcRenderer.on(channel, handler);
    return () => ipcRenderer.removeListener(channel, handler);
  }
};

contextBridge.exposeInMainWorld('medirian', bridge);
