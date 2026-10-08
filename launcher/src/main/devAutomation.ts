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
 *  MEDIRIAN_DEV_CAPTURE=<dir>      screenshot every page (and the setup wizard) into <dir>
 *  MEDIRIAN_DEV_LAUNCH=<profile>   sign in offline and launch the profile whose name contains <profile>
 *                                  (otherwise a new profile for the target with that id, e.g. 1.21.8)
 *  MEDIRIAN_DEV_OFFLINE=<name>     offline player name for MEDIRIAN_DEV_LAUNCH (default MedirianDev)
 *  MEDIRIAN_DEV_RUN_MS=<ms>        stop the game and quit this long after it started
 *
 * Used to verify the launcher visually and to test the real install/launch pipeline end to end.
 */
export async function runDevAutomation(
  window: BrowserWindow,
  deps: { settings: SettingsStore; accounts: AccountService; profiles: ProfileStore; game: GameService }
): Promise<void> {
  const captureDir = process.env.MEDIRIAN_DEV_CAPTURE;
  const launch = process.env.MEDIRIAN_DEV_LAUNCH;
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
    await run('window.__medirianDev.completeSetup()');
    await wait(1200);
  }
  // with MEDIRIAN_DEV_OFFLINE the screenshots show that player's real skin
  if (process.env.MEDIRIAN_DEV_OFFLINE) {
    await deps.accounts.offline(process.env.MEDIRIAN_DEV_OFFLINE);
    await wait(2500);
  }
  for (const page of ['home', 'mods', 'profiles', 'changelog', 'settings']) {
    await run(`window.__medirianDev.navigate('${page}')`);
    await wait(900);
    await capture(`page-${page}`);
    if (page === 'mods') {
      // the search results load from Modrinth
      await wait(3500);
      await capture('page-mods-results');
    }
  }
  // the top bar with a launcher update waiting, at the default and at the smallest window width
  await run("window.__medirianDev.navigate('home')");
  await run("window.__medirianDev.updateReady('0.3.1')");
  await wait(500);
  await capture('topbar-update');
  const [width, height] = window.getSize();
  window.setSize(980, height);
  await wait(700);
  await capture('topbar-update-narrow');
  window.setSize(width, height);
  await run('window.__medirianDev.updateReady(null)');
  await run("window.__medirianDev.navigate('settings')");
  await wait(700);
  // settings tabs beyond the first one
  await run(`[...document.querySelectorAll('.settings__tab')].find((tab) => tab.textContent === 'Discord')?.click()`);
  await wait(600);
  await capture('page-settings-discord');
  await run("window.__medirianDev.navigate('home')");
  await run('window.__medirianDev.account(true)');
  await wait(600);
  await capture('dialog-account');
  await run('window.__medirianDev.account(false)');
  // profile codes: share the selected profile, then look the code up in the import dialog
  await run("window.__medirianDev.navigate('profiles')");
  await wait(700);
  await run(`[...document.querySelectorAll('.profile-editor__actions button')].find((b) => b.querySelector('svg') && b.textContent.match(/Share|Udostępnij/))?.click()`);
  await wait(2500);
  await capture('dialog-share');
  const code = await run(`document.querySelector('.share__code')?.textContent ?? ''`);
  await run(`document.querySelector('.share [aria-label]')?.click()`);
  await wait(400);
  await run(`[...document.querySelectorAll('.window__actions button')].find((b) => b.textContent.match(/Import/))?.click()`);
  await wait(500);
  if (code) {
    await run(`(() => {
      const input = document.querySelector('.share__input input');
      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      set.call(input, ${JSON.stringify(String(code).toLowerCase())});
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    })()`);
    await wait(2000);
  }
  await capture('dialog-import');
  await run(`document.querySelector('.share [aria-label]')?.click()`);
  await run("window.__medirianDev.navigate('home')");
  await wait(400);
  // winter snow without touching the saved settings
  await run("document.documentElement.dataset.snow = 'on'");
  await wait(900);
  await capture('page-home-winter');
  await run('window.__medirianDev.reload()');

  if (!launch) {
    app.quit();
    return;
  }
  await deps.accounts.offline(process.env.MEDIRIAN_DEV_OFFLINE || 'MedirianDev');
  const profile =
    deps.profiles.list().find((p) => p.name.toLowerCase().includes(launch.toLowerCase())) ??
    deps.profiles.list().find((p) => p.targetId === launch) ??
    (await deps.profiles.create({ name: `Dev ${launch}`, targetId: launch }));
  await deps.settings.update({ selectedProfileId: profile.id });
  await run('window.__medirianDev.reload()');
  log.info(`[dev] launching ${profile.name}`);
  void deps.game.launch(profile.id);

  let lastLabel = '';
  let capturedPreparing = false;
  let startedAt = 0;
  const runMs = Number(process.env.MEDIRIAN_DEV_RUN_MS || 0);
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
