import type { MedirianBridge } from '../common/ipc';

declare global {
  interface Window {
    medirian: MedirianBridge;
  }
}

export {};
