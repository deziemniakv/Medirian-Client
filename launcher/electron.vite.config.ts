import { resolve } from 'node:path';
import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { checkUrl, PUBLIC_CONFIG_KEYS, type BuildConfig } from './src/main/core/config';

const alias = { '@common': resolve(__dirname, 'src/common') };

/**
 * The owner's public configuration from the repository's `.env` (and MEDIRIAN_* environment
 * variables, which win — that is how the release workflow passes its repository variables).
 * Only PUBLIC_CONFIG_KEYS are embedded; nothing else from `.env` reaches the app.
 */
function buildConfig(mode: string): BuildConfig {
  const env = loadEnv(mode, resolve(__dirname, '..'), 'MEDIRIAN_');
  const config = Object.fromEntries(PUBLIC_CONFIG_KEYS.map((key) => [key, (env[key] ?? '').trim()])) as BuildConfig;
  for (const key of ['MEDIRIAN_SERVICES_URL', 'MEDIRIAN_MANIFEST_URL'] as const) {
    const result = checkUrl(key, config[key]);
    if ('problem' in result) {
      // production addresses must be HTTPS: refuse to build a launcher that would talk plain HTTP
      throw new Error(`.env: ${result.problem}`);
    }
  }
  return config;
}

export default defineConfig(({ mode }) => ({
  main: {
    plugins: [externalizeDepsPlugin()],
    resolve: { alias },
    define: { __MEDIRIAN_CONFIG__: JSON.stringify(buildConfig(mode)) },
    build: { rollupOptions: { input: { index: resolve(__dirname, 'src/main/index.ts') } } }
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    resolve: { alias },
    build: { rollupOptions: { input: { index: resolve(__dirname, 'src/preload/index.ts') } } }
  },
  renderer: {
    root: resolve(__dirname, 'src/renderer'),
    resolve: { alias },
    plugins: [react()],
    build: {
      minify: 'esbuild',
      rollupOptions: { input: { index: resolve(__dirname, 'src/renderer/index.html') } }
    }
  }
}));
