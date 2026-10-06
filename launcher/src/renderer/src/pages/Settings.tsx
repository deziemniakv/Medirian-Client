import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import type { DiskUsage, JavaInstall, LauncherSettings, ReleaseTarget } from '../../../common/types';
import { errorMessage, invoke } from '../api';
import { ArtIcon, Icon, type IconName } from '../components/Icon';
import { useT, type MessageKey } from '../i18n';
import { useStore } from '../store';

type Section = 'general' | 'updates' | 'installation' | 'java' | 'account' | 'discord' | 'developer' | 'about';

const SECTIONS: { id: Section; label: MessageKey; icon: IconName }[] = [
  { id: 'general', label: 'settings.general', icon: 'gear' },
  { id: 'updates', label: 'settings.updates', icon: 'download' },
  { id: 'installation', label: 'settings.installation', icon: 'folder' },
  { id: 'java', label: 'settings.java', icon: 'cpu' },
  { id: 'account', label: 'settings.account', icon: 'user' },
  { id: 'discord', label: 'settings.discord', icon: 'globe' },
  { id: 'developer', label: 'settings.developer', icon: 'terminal' },
  { id: 'about', label: 'settings.about', icon: 'info' }
];

function Toggle({ on, label, disabled, onChange }: { on: boolean; label: string; disabled?: boolean; onChange: () => void }) {
  return <button className={`toggle${on ? ' toggle--on' : ''}`} disabled={disabled} aria-label={label} aria-pressed={on} onClick={onChange} />;
}

const NO_TARGETS: ReleaseTarget[] = [];

function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) {
    return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
  }
  return `${Math.max(0, Math.round(bytes / 1024 ** 2))} MB`;
}

function Row({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="setting-row">
      <div className="setting-row__text">
        <div className="setting-row__label pixel">{label}</div>
        {hint && <div className="setting-row__hint">{hint}</div>}
      </div>
      <div className="setting-row__control">{children}</div>
    </div>
  );
}

export function Settings() {
  const t = useT();
  const [section, setSection] = useState<Section>('general');
  const settings = useStore((s) => s.settings)!;
  const update = useStore((s) => s.updateSettings);
  const app = useStore((s) => s.app);
  const set = (patch: Partial<LauncherSettings>) => void update(patch);

  const current = SECTIONS.find((s) => s.id === section)!;
  return (
    <div className="page">
      <div className="window px-frame">
        <div className="window__header">
          <ArtIcon name="settings" scale={2} />
          <h1 className="window__title">{t('settings.title')}</h1>
        </div>
        <div className="settings">
        <nav className="settings__nav px-inset">
          {SECTIONS.map((s) => (
            <button key={s.id} className={`settings__tab pixel${section === s.id ? ' settings__tab--active' : ''}`} onClick={() => setSection(s.id)}>
              <Icon name={s.icon} size={16} />
              {t(s.label)}
            </button>
          ))}
        </nav>
        <div className="settings__content">
          <h2 className="settings__heading pixel"><Icon name={current.icon} size={16} />{t(current.label)}</h2>
          {section === 'general' && (
            <>
              <Row label={t('settings.language')}>
                <select className="select" value={settings.language} onChange={(e) => set({ language: e.target.value as LauncherSettings['language'] })}>
                  <option value="en">English</option>
                  <option value="pl">Polski</option>
                  <option value="de">Deutsch</option>
                  <option value="es">Español</option>
                </select>
              </Row>
              <Row label={t('settings.sceneMotion')} hint={t('settings.sceneMotionHint')}>
                <Toggle on={settings.sceneMotion} label={t('settings.sceneMotion')} onChange={() => set({ sceneMotion: !settings.sceneMotion })} />
              </Row>
              <Row label={t('settings.winterSnow')} hint={t('settings.winterSnowHint')}>
                <Toggle on={settings.winterSnow} label={t('settings.winterSnow')} onChange={() => set({ winterSnow: !settings.winterSnow })} />
              </Row>
              <Row label={t('settings.afterLaunch')}>
                <select className="select" value={settings.afterLaunch} onChange={(e) => set({ afterLaunch: e.target.value as LauncherSettings['afterLaunch'] })}>
                  <option value="keep">{t('settings.afterKeep')}</option>
                  <option value="minimize">{t('settings.afterMinimize')}</option>
                </select>
              </Row>
            </>
          )}
          {section === 'updates' && <UpdatesSection />}
          {section === 'installation' && <InstallationSection />}
          {section === 'java' && <JavaSection />}
          {section === 'account' && <AccountSection />}
          {section === 'discord' && <DiscordSection />}
          {section === 'developer' && (
            <>
              <Row label={t('settings.msaClientId')} hint="MEDIRIAN_MSA_CLIENT_ID">
                <input className="input mono settings__wide-input" value={settings.msaClientId}
                  placeholder="00000000-0000-0000-0000-000000000000"
                  onChange={(e) => set({ msaClientId: e.target.value.trim() })} />
              </Row>
              <Row label={t('settings.servicesUrl')} hint="MEDIRIAN_SERVICES_URL">
                <input className="input mono settings__wide-input" value={settings.servicesUrl}
                  placeholder={app?.defaultServicesUrl || 'https://…'}
                  onChange={(e) => set({ servicesUrl: e.target.value.trim() })} />
              </Row>
            </>
          )}
          {section === 'about' && <AboutSection />}
        </div>
        </div>
      </div>
    </div>
  );
}

function LauncherUpdateRow() {
  const t = useT();
  const status = useStore((s) => s.launcherUpdate);
  const version = useStore((s) => s.app?.version ?? '');
  if (status.state === 'unsupported') {
    return null;
  }
  const label = status.state === 'checking' ? t('launcherUpdate.checking')
    : status.state === 'downloading' ? t('launcherUpdate.downloading', { version: status.version ?? '', percent: status.percent ?? 0 })
    : status.state === 'ready' ? t('launcherUpdate.ready', { version: status.version ?? '' })
    : status.state === 'latest' ? t('launcherUpdate.latest', { version })
    : t('launcherUpdate.current', { version });
  return (
    <Row label={label} hint={status.state === 'error' ? t('launcherUpdate.error', { error: status.error ?? '' }) : undefined}>
      {status.state === 'ready' ? (
        <button className="btn btn--primary" onClick={() => void invoke('launcherUpdate:install')}>
          <Icon name="refresh" size={16} />
          {t('launcherUpdate.restartShort')}
        </button>
      ) : (
        <button className="btn" disabled={status.state === 'checking' || status.state === 'downloading'}
          onClick={() => void invoke('launcherUpdate:check')}>
          {status.state === 'checking' ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
          {t('launcherUpdate.check')}
        </button>
      )}
    </Row>
  );
}

function UpdatesSection() {
  const t = useT();
  const settings = useStore((s) => s.settings)!;
  const update = useStore((s) => s.updateSettings);
  const releases = useStore((s) => s.releases);
  const refresh = useStore((s) => s.refreshReleases);
  const app = useStore((s) => s.app);
  const [checking, setChecking] = useState(false);

  const check = async () => {
    setChecking(true);
    await refresh(true);
    setChecking(false);
  };

  return (
    <>
      <Row label={t('settings.channel')}>
        <select className="select" value={settings.updateChannel}
          onChange={(e) => void update({ updateChannel: e.target.value as LauncherSettings['updateChannel'] }).then(() => refresh(true))}>
          <option value="stable">{t('settings.channelStable')}</option>
          <option value="local">{t('settings.channelLocal')}</option>
        </select>
      </Row>
      {settings.updateChannel === 'stable' ? (
        <Row label={t('settings.manifestUrl')}>
          <input className="input mono settings__wide-input" value={settings.manifestUrl} placeholder={app?.defaultManifestUrl || 'https://…/release-manifest.json'}
            onChange={(e) => void update({ manifestUrl: e.target.value.trim() })} />
        </Row>
      ) : (
        <Row label={t('settings.localDir')}>
          <input className="input mono settings__wide-input" value={settings.localDistributionDir}
            onChange={(e) => void update({ localDistributionDir: e.target.value.trim() })} />
        </Row>
      )}
      <Row label={releases?.manifest ? t('settings.releaseOk', { version: releases.manifest.client.version, count: releases.manifest.targets.length }) : '—'}
        hint={releases?.error ?? undefined}>
        <button className="btn" disabled={checking} onClick={() => void check()}>
          {checking ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
          {checking ? t('settings.checking') : t('settings.checkNow')}
        </button>
      </Row>
      <LauncherUpdateRow />
    </>
  );
}

function InstallationSection() {
  const t = useT();
  const settings = useStore((s) => s.settings)!;
  const update = useStore((s) => s.updateSettings);
  const app = useStore((s) => s.app);
  const targets = useStore((s) => s.releases?.manifest?.targets) ?? NO_TARGETS;
  const [usage, setUsage] = useState<DiskUsage | null>(null);
  const [repairing, setRepairing] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const repair = async (targetId: string) => {
    setRepairing(targetId);
    setError(null);
    try {
      const report = await invoke('install:repair', targetId);
      setMessage(`${targetId}: ${t('settings.repairDone', { checked: report.checkedFiles, repaired: report.repairedFiles })}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setRepairing(null);
    }
  };

  return (
    <>
      <Row label={t('settings.dataFolder')} hint={app?.home}>
        <button className="btn" onClick={() => void invoke('shell:open', 'home')}>
          <Icon name="folder" size={16} />
          {t('settings.open')}
        </button>
      </Row>
      <Row label={t('settings.diskUsage')}
        hint={usage ? `Java ${formatBytes(usage.runtimeBytes)} · Minecraft ${formatBytes(usage.gameBytes)} · Medirian ${formatBytes(usage.clientsBytes)} · Cache ${formatBytes(usage.cacheBytes)}` : undefined}>
        <button className="btn" onClick={() => void invoke('install:diskUsage').then(setUsage)}>{t('settings.calculate')}</button>
      </Row>
      <Row label={t('settings.repair')} hint={t('settings.repairHint')}>
        {targets.map((target) => (
          <button key={target.id} className="btn" disabled={repairing !== null} onClick={() => void repair(target.id)}>
            {repairing === target.id ? <span className="spinner" /> : <Icon name="shield" size={16} />}
            {target.displayName}
          </button>
        ))}
      </Row>
      <Row label={t('settings.clearCache')} hint={t('settings.clearCacheHint')}>
        <button className="btn" onClick={() => void invoke('install:clearCache').then((freed) => setMessage(t('settings.cleared', { size: formatBytes(freed) })))}>
          <Icon name="trash" size={16} />
          {t('settings.clearCache')}
        </button>
      </Row>
      <Row label={t('settings.downloads')}>
        <input className="range settings__range" type="range" min={2} max={32} value={settings.concurrentDownloads}
          style={{ '--p': `${((settings.concurrentDownloads - 2) / 30) * 100}%` } as CSSProperties}
          onChange={(e) => void update({ concurrentDownloads: Number(e.target.value) })} />
        <span className="mono settings__range-value">{settings.concurrentDownloads}</span>
      </Row>
      <Row label={t('settings.reuseAssets')}>
        <Toggle on={settings.reuseMinecraftAssets} label={t('settings.reuseAssets')}
          onChange={() => void update({ reuseMinecraftAssets: !settings.reuseMinecraftAssets })} />
      </Row>
      {message && <div className="alert">{message}</div>}
      {error && <div className="alert alert--error">{error}</div>}
    </>
  );
}

function JavaSection() {
  const t = useT();
  const [javas, setJavas] = useState<JavaInstall[] | null>(null);
  const scan = () => {
    setJavas(null);
    void invoke('java:detect').then(setJavas);
  };
  useEffect(scan, []);
  return (
    <>
      <p className="dim settings__intro">{t('settings.javaHint')}</p>
      {javas === null ? <div className="row dim"><span className="spinner" /></div> : javas.length === 0 ? (
        <p className="muted">{t('settings.javaNone')}</p>
      ) : (
        <ul className="java-list">
          {javas.map((java) => (
            <li key={java.path}>
              <span className={`chip ${java.source === 'medirian' ? 'chip--pumpkin' : 'chip--muted'}`}>Java {java.major}</span>
              <span className="java-list__version">{java.version}</span>
              <span className="muted">{java.vendor}</span>
              <span className="mono java-list__path">{java.path}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="settings__footer">
        <button className="btn" onClick={scan}><Icon name="refresh" size={16} />{t('settings.javaScan')}</button>
      </div>
    </>
  );
}

function AccountSection() {
  const t = useT();
  const account = useStore((s) => s.account);
  const open = useStore((s) => s.setAccountDialog);
  return (
    <Row label={account ? t('account.signedInAs', { name: account.name }) : t('account.notSignedIn')}
      hint={account ? (account.type === 'microsoft' ? t('account.microsoft') : t('account.offline')) : undefined}>
      {account ? (
        <button className="btn btn--danger" onClick={() => void invoke('account:logout')}>{t('account.signOut')}</button>
      ) : (
        <button className="btn btn--primary" onClick={() => open(true)}>{t('account.signIn')}</button>
      )}
    </Row>
  );
}

function DiscordSection() {
  const t = useT();
  const settings = useStore((s) => s.settings)!;
  const update = useStore((s) => s.updateSettings);
  const status = useStore((s) => s.discord);
  // the id is applied when editing ends, not on every keystroke (each change reconnects)
  const [appId, setAppId] = useState(settings.discordAppId);
  const commitAppId = () => {
    if (appId !== settings.discordAppId) {
      void update({ discordAppId: appId });
    }
  };
  const toggle = (key: 'discordPresence' | 'discordShowServer' | 'discordShowInLauncher', label: string, disabled = false) => (
    <Toggle on={settings[key]} label={label} disabled={disabled} onChange={() => void update({ [key]: !settings[key] })} />
  );
  const off = !settings.discordPresence;
  return (
    <>
      <Row label={t('discord.enable')} hint={t('discord.enableHint')}>{toggle('discordPresence', t('discord.enable'))}</Row>
      <Row label={t('discord.showServer')}>{toggle('discordShowServer', t('discord.showServer'), off)}</Row>
      <Row label={t('discord.showInLauncher')}>{toggle('discordShowInLauncher', t('discord.showInLauncher'), off)}</Row>
      <Row label={t('discord.status')} hint={status.state === 'error' ? status.error : undefined}>
        <span className={`discord-status discord-status--${status.state}`}>
          {t(`discord.state.${status.state}` as MessageKey, { user: status.user ?? '' })}
        </span>
      </Row>
      <Row label={t('discord.appId')} hint={t('discord.appIdHint')}>
        <input className="input mono settings__wide-input" value={appId} placeholder="123456789012345678" inputMode="numeric"
          onChange={(e) => setAppId(e.target.value.replace(/D/g, ''))} onBlur={commitAppId}
          onKeyDown={(e) => e.key === 'Enter' && commitAppId()} />
      </Row>
      {status.state === 'unconfigured' && (
        <div className="alert discord-setup">
          <span>{t('discord.setup')}</span>
          <button className="btn" onClick={() => void invoke('shell:openExternal', 'https://discord.com/developers/applications')}>
            <Icon name="external" size={16} />
            {t('discord.openPortal')}
          </button>
        </div>
      )}
    </>
  );
}

function AboutSection() {
  const t = useT();
  const app = useStore((s) => s.app);
  const releases = useStore((s) => s.releases);
  if (!app) {
    return null;
  }
  return (
    <>
      <Row label="Medirian Launcher" hint={`Electron ${app.electron} · Chromium ${app.chrome} · Node ${app.node}`}>
        <span className="chip">v{app.version}</span>
      </Row>
      <Row label="Medirian Client">
        <span className="chip chip--muted">{releases?.manifest ? `v${releases.manifest.client.version}` : '—'}</span>
      </Row>
      <Row label={t('settings.logs')}>
        <button className="btn" onClick={() => void invoke('shell:open', 'logs')}>
          <Icon name="folder" size={16} />
          {t('settings.open')}
        </button>
      </Row>
    </>
  );
}
