import { app, type BrowserWindow } from 'electron';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { AccountService } from './auth/accounts';
import { log } from './core/log';
import type { SettingsStore } from './core/settings';
import type { GameService } from './launch/game';
import type { ProfileStore } from './profiles/profiles';

/**
 * Development-only automation (never active in packaged builds), driven by environment variables:
 *
 *  MERIDIAN_DEV_CAPTURE=<dir>      screenshot every page (and the setup wizard) into <dir>
 *  MERIDIAN_DEV_LAUNCH=<profile>   sign in offline and launch the profile whose name contains <profile>
 *  MERIDIAN_DEV_OFFLINE=<name>     offline player name for MERIDIAN_DEV_LAUNCH (default MeridianDev)
 *  MERIDIAN_DEV_RUN_MS=<ms>        stop the game and quit this long after it started
 *
 * Used to verify the launcher visually and to test the real install/launch pipeline end to end.
 */
export async function runDevAutomation(
  window: BrowserWindow,
  deps: { settings: SettingsStore; accounts: AccountService; profiles: ProfileStore; game: GameService }
): Promise<void> {
  const captureDir = process.env.MERIDIAN_DEV_CAPTURE;
  const launch = process.env.MERIDIAN_DEV_LAUNCH;
  if (app.isPackaged || (!captureDir && !launch)) {
    return;
  }
  const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
  const run = (code: string) => window.webContents.executeJavaScript(code);
  const capture = async (name: string) => {
    if (!captureDir) {
      return;
    }
    await mkdir(captureDir, { recursive: true });
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const image = await window.webContents.capturePage(undefined, { stayHidden: true });
        await writeFile(join(captureDir, `${name}.png`), image.toPNG());
        log.info(`[dev] captured ${name}`);
        return;
      } catch (error) {
        log.warn(`[dev] capture of ${name} failed (attempt ${attempt + 1})`, error);
        await wait(700);
      }
    }
  };

  await wait(2500);
  if (!deps.settings.get().setupCompleted) {
    await wait(4000); // let the diagnostics finish
    await capture('0-setup');
    await run('window.__meridianDev.completeSetup()');
    await wait(1200);
  }
  for (const page of ['home', 'profiles', 'changelog', 'settings']) {
    await run(`window.__meridianDev.navigate('${page}')`);
    await wait(900);
    await capture(`page-${page}`);
  }
  await run("window.__meridianDev.navigate('home')");

  if (!launch) {
    app.quit();
    return;
  }
  await deps.accounts.offline(process.env.MERIDIAN_DEV_OFFLINE || 'MeridianDev');
  const profile = deps.profiles.list().find((p) => p.name.toLowerCase().includes(launch.toLowerCase()));
  if (!profile) {
    log.error(`[dev] no profile matching "${launch}"`);
    return;
  }
  await deps.settings.update({ selectedProfileId: profile.id });
  await run('window.__meridianDev.reload()');
  log.info(`[dev] launching ${profile.name}`);
  void deps.game.launch(profile.id);

  let lastLabel = '';
  let capturedPreparing = false;
  let startedAt = 0;
  const runMs = Number(process.env.MERIDIAN_DEV_RUN_MS || 0);
  for (;;) {
    await wait(500);
    const state = deps.game.current();
    if (state.state === 'preparing') {
      if (state.progress.label !== lastLabel) {
        lastLabel = state.progress.label;
        log.info(`[dev] ${state.progress.label}`);
      }
      if (!capturedPreparing && state.progress.phase === 'assets') {
        capturedPreparing = true;
        await capture('launch-1-preparing');
      }
    } else if (state.state === 'running') {
      if (!startedAt) {
        startedAt = Date.now();
        log.info(`[dev] Minecraft running (pid ${state.pid})`);
      }
      if (state.bridge.connected && state.bridge.state && captureDir) {
        await capture('launch-2-running');
      }
      if (runMs > 0 && Date.now() - startedAt > runMs) {
        log.info(`[dev] bridge: ${JSON.stringify(state.bridge)}`);
        deps.game.kill();
      }
    } else if (state.state === 'error') {
      log.error(`[dev] launch failed: ${state.message}`);
      await capture('launch-error');
      break;
    } else if (state.state === 'exited') {
      log.info(`[dev] Minecraft exited (code ${state.code}, crashed ${state.crashed})`);
      break;
    }
  }
  if (runMs > 0) {
    app.quit();
  }
}
