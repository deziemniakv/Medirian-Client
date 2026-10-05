import { app, BrowserWindow, ipcMain, shell } from 'electron';
import { existsSync } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import type { EventApi, EventChannel, InvokeApi, InvokeChannel } from '../common/ipc';
import type { OpenTarget } from '../common/types';
import { AccountService } from './auth/accounts';
import { CHANGELOG } from './changelog';
import { runDevAutomation } from './devAutomation';
import { initLog, log } from './core/log';
import { instanceDir, medirianPaths } from './core/paths';
import { defaultManifestUrl, defaultServicesUrl, defaultSettings, SettingsStore } from './core/settings';
import { buildActivity, DiscordPresenceService } from './discord/presence';
import { Installer } from './install/installer';
import { SetupService, systemInfo } from './install/setup';
import { clearCache, diskUsage } from './install/storage';
import { detectJavaInstallations } from './java/detect';
import { JavaRuntimeService } from './java/runtime';
import { GameService } from './launch/game';
import { LoaderService } from './minecraft/loader';
import { MojangService } from './minecraft/mojang';
import { listClientConfigProfiles, ProfileStore } from './profiles/profiles';
import { LauncherUpdateService, type Updater } from './updates/launcherUpdate';
import { UpdateService } from './updates/updates';

const EXTERNAL_ALLOWED = [/^https:\/\/(www\.)?microsoft\.com\//, /^https:\/\/(www\.)?minecraft\.net\//, /^https:\/\/login\.live\.com\//,
  /^https:\/\/discord\.com\/developers\//];

let window: BrowserWindow | null = null;

function emit<K extends EventChannel>(channel: K, payload: EventApi[K]): void {
  window?.webContents.send(channel, payload);
}

function handle<K extends InvokeChannel>(channel: K, handler: (...args: Parameters<InvokeApi[K]>) => ReturnType<InvokeApi[K]> | Promise<Awaited<ReturnType<InvokeApi[K]>>>): void {
  ipcMain.handle(channel, async (_event, ...args) => handler(...(args as Parameters<InvokeApi[K]>)));
}

type Services = Parameters<typeof runDevAutomation>[1];

/** electron-updater, only in installed builds that were published with an update feed (app-update.yml). */
async function loadUpdater(): Promise<Updater | null> {
  if (!app.isPackaged || !existsSync(join(process.resourcesPath, 'app-update.yml'))) {
    return null;
  }
  // a CommonJS module whose autoUpdater is a lazy getter, which import() does not expose as a named export
  const updaterModule = await import('electron-updater');
  const autoUpdater = (updaterModule.default as unknown as typeof updaterModule).autoUpdater;
  // Windows: the full installer is published, not a web installer
  (autoUpdater as { disableWebInstaller?: boolean }).disableWebInstaller = true;
  autoUpdater.logger = {
    info: (message: unknown) => log.info(`[updater] ${String(message)}`),
    warn: (message: unknown) => log.warn(`[updater] ${String(message)}`),
    error: (message: unknown) => log.warn(`[updater] ${String(message)}`)
  };
  return autoUpdater as unknown as Updater;
}

async function bootstrap(): Promise<Services> {
  const paths = medirianPaths();
  await mkdir(paths.launcher, { recursive: true });
  await initLog(paths.logs);
  log.info(`Medirian Launcher ${app.getVersion()} starting (home: ${paths.root})`);

  // In development the local channel points at the repository's distribution folder.
  const devDistribution = app.isPackaged ? '' : resolve(app.getAppPath(), '..', 'distribution');
  const settings = await SettingsStore.load(paths.settingsFile, defaultSettings(devDistribution));
  const profiles = new ProfileStore(paths.profilesFile);
  await profiles.load();

  const msaClientId = () => settings.get().msaClientId || import.meta.env.MAIN_VITE_MSA_CLIENT_ID || process.env.MEDIRIAN_MSA_CLIENT_ID || '';
  const accounts = new AccountService(
    paths.accountsFile,
    msaClientId,
    !app.isPackaged,
    (account) => emit('account:changed', account),
    (result) => emit('account:loginResult', result)
  );
  await accounts.load();

  const mojang = new MojangService(paths);
  const loader = new LoaderService(paths);
  const runtimes = new JavaRuntimeService(paths);
  const updates = new UpdateService(paths, () => settings.get());
  const installer = new Installer(paths, mojang, loader, runtimes, updates,
    () => settings.get().concurrentDownloads, () => settings.get().reuseMinecraftAssets);
  const discord = new DiscordPresenceService({
    enabled: () => settings.get().discordPresence,
    applicationId: () => settings.get().discordAppId || import.meta.env.MAIN_VITE_DISCORD_APP_ID || process.env.MEDIRIAN_DISCORD_APP_ID || '',
    activity: () => buildActivity({
      game: game.current(),
      targetName: (targetId) => updates.target(targetId)?.displayName ?? null,
      profileTarget: (profileId) => profiles.get(profileId)?.targetId ?? null,
      version: app.getVersion(),
      language: settings.get().language,
      showServer: settings.get().discordShowServer,
      showInLauncher: settings.get().discordShowInLauncher
    }),
    emitStatus: (status) => emit('discord:status', status),
    log
  });
  app.on('before-quit', () => discord.dispose());
  const launcherUpdates = new LauncherUpdateService(await loadUpdater(), (status) => emit('launcherUpdate:status', status), log);
  launcherUpdates.start();
  app.on('before-quit', () => launcherUpdates.dispose());
  const game = new GameService(paths, installer, updates, profiles, accounts, app.getVersion(),
    (state) => {
      emit('game:state', state);
      discord.refresh();
    },
    (lines) => emit('game:log', lines),
    () => {
      if (settings.get().afterLaunch === 'minimize') {
        window?.minimize();
      }
    },
    () => settings.get().servicesUrl || defaultServicesUrl());
  const setup = new SetupService(paths, updates);

  handle('app:info', () => ({
    version: app.getVersion(),
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
    packaged: app.isPackaged,
    home: paths.root,
    platform: process.platform,
    defaultManifestUrl: defaultManifestUrl(),
    defaultServicesUrl: defaultServicesUrl()
  }));
  handle('window:minimize', () => window?.minimize());
  handle('window:close', () => window?.close());

  handle('settings:get', () => settings.get());
  handle('settings:update', async (patch) => {
    const next = await settings.update(patch);
    discord.refresh();
    return next;
  });

  handle('profiles:list', () => profiles.list());
  handle('profiles:create', (base) => profiles.create(base));
  handle('profiles:save', (profile) => profiles.save(profile));
  handle('profiles:delete', (id) => profiles.remove(id));
  handle('client:configProfiles', () => listClientConfigProfiles(paths.clientProfiles));

  handle('releases:get', (refresh) => updates.refresh(refresh));
  handle('targets:status', async () => {
    await updates.refresh(false);
    return updates.statuses(paths.versions);
  });
  handle('changelog:get', () => CHANGELOG);
  handle('launcherUpdate:status', () => launcherUpdates.current());
  handle('launcherUpdate:check', () => launcherUpdates.check());
  handle('launcherUpdate:install', () => launcherUpdates.install());

  handle('game:state', () => game.current());
  handle('game:launch', (profileId) => {
    void game.launch(profileId);
  });
  handle('game:kill', () => game.kill());
  handle('game:log', () => game.log());

  handle('install:repair', async (targetId) => {
    await updates.refresh(false);
    const target = updates.target(targetId);
    if (!target) {
      throw new Error(`Unknown target ${targetId}`);
    }
    return installer.repair(target, () => undefined);
  });
  handle('install:clearCache', () => clearCache(paths));
  handle('install:diskUsage', () => diskUsage(paths));

  handle('java:detect', () => detectJavaInstallations(paths.runtime));

  handle('account:get', () => accounts.current());
  handle('account:loginStart', () => accounts.startLogin());
  handle('account:loginCancel', () => accounts.cancelLogin());
  handle('account:logout', () => accounts.logout());
  handle('account:offline', (name) => accounts.offline(name));
  handle('account:offlineAllowed', () => accounts.isOfflineAllowed());

  handle('discord:status', () => discord.current());

  handle('system:info', () => systemInfo(paths));
  handle('setup:run', () => setup.run());
  handle('setup:fix', (id) => setup.fix(id));

  handle('shell:open', async (target: OpenTarget, targetId?: string) => {
    const dirs: Record<OpenTarget, string> = {
      home: paths.root,
      logs: paths.logs,
      instance: instanceDir(paths, targetId ?? ''),
      screenshots: join(instanceDir(paths, targetId ?? ''), 'screenshots')
    };
    const dir = dirs[target];
    await mkdir(dir, { recursive: true });
    await shell.openPath(dir);
  });
  handle('shell:openExternal', async (url) => {
    if (!EXTERNAL_ALLOWED.some((pattern) => pattern.test(url))) {
      throw new Error('This link is not allowed.');
    }
    await shell.openExternal(url);
  });
  discord.refresh();
  return { settings, accounts, profiles, game };
}

function createWindow(): void {
  // development screenshot runs place the window off-screen so nobody can interact with it
  const offscreen = !app.isPackaged && !!process.env.MEDIRIAN_DEV_CAPTURE && !process.env.MEDIRIAN_DEV_LAUNCH;
  window = new BrowserWindow({
    ...(offscreen ? { x: -5000, y: 0, focusable: false, skipTaskbar: true } : {}),
    width: 1180,
    height: 740,
    minWidth: 980,
    minHeight: 640,
    show: false,
    frame: false,
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'hidden',
    backgroundColor: '#0A0810',
    icon: join(__dirname, '../../resources/icon.png'),
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      backgroundThrottling: !offscreen
    }
  });
  window.once('ready-to-show', () => (offscreen ? window?.showInactive() : window?.show()));
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith('http://localhost') && !url.startsWith('file://')) {
      event.preventDefault();
    }
  });
  if (!app.isPackaged && process.env.ELECTRON_RENDERER_URL) {
    void window.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    void window.loadFile(join(__dirname, '../renderer/index.html'));
  }
  window.on('closed', () => {
    window = null;
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (window) {
      if (window.isMinimized()) {
        window.restore();
      }
      window.focus();
    }
  });
  app.whenReady().then(async () => {
    let services: Services | null = null;
    try {
      services = await bootstrap();
    } catch (error) {
      log.error('Launcher failed to start', error);
    }
    createWindow();
    if (services && window) {
      void runDevAutomation(window, services);
    }
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
      }
    });
  });
  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      app.quit();
    }
  });
}

