import type { MeridianBridge } from '../common/ipc';

declare global {
  interface Window {
    meridian: MeridianBridge;
  }
}

export {};
