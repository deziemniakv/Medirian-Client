import { useEffect, useRef, useState } from 'react';
import type { LaunchProfile, ReleaseTarget } from '../../../common/types';
import { invoke } from '../api';
import { useT, type MessageKey } from '../i18n';
import { selectedProfile, useStore } from '../store';
import { ArtIcon, Icon } from './Icon';

const NO_TARGETS: ReleaseTarget[] = [];

function percent(done: number, total: number): number {
  return total > 0 ? Math.min(100, Math.round((done / total) * 100)) : 0;
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) {
    return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
  }
  return `${Math.round(bytes / 1024 ** 2)} MB`;
}

/** The icon of a profile's version: a sword for PvP, the moon for the newest, a grass block otherwise. */
export function targetIcon(target: ReleaseTarget | undefined): string {
  if (target?.tags?.includes('pvp')) {
    return 'cat-combat';
  }
  if (target?.tags?.includes('latest')) {
    return 'moon';
  }
  return 'cat-world';
}

/** The launch bar at the bottom of the home screen: profile, status and the PLAY button. */
export function LaunchBar() {
  const t = useT();
  const state = useStore();
  const profile = selectedProfile(state);
  const target = state.releases?.manifest?.targets.find((x) => x.id === profile?.targetId);
  const status = state.statuses.find((x) => x.targetId === profile?.targetId);
  const game = state.game;
  const busy = game.state === 'preparing' || game.state === 'running';
  const preparing = game.state === 'preparing' ? game.progress : null;

  let headline: string;
  if (game.state === 'running') {
    headline = t('home.running');
  } else if (preparing) {
    headline = t(`phase.${preparing.phase}` as MessageKey);
  } else if (!target) {
    headline = t('home.unavailable');
  } else if (status && !status.installedVersion) {
    headline = t('home.install');
  } else if (status?.updateAvailable) {
    headline = t('home.update');
  } else {
    headline = t('home.ready');
  }

  const progressValue = preparing
    ? preparing.bytesTotal ? percent(preparing.bytesDone ?? 0, preparing.bytesTotal) : percent(preparing.done, preparing.total)
    : 0;

  const onPlay = () => {
    if (!state.account) {
      state.setAccountDialog(true);
      return;
    }
    void state.launch();
  };

  let detail: { text: string; error?: boolean } | null = null;
  if (preparing) {
    detail = {
      text: preparing.bytesTotal
        ? `${formatBytes(preparing.bytesDone ?? 0)} / ${formatBytes(preparing.bytesTotal)} · ${progressValue}%`
        : preparing.total > 1 ? `${preparing.done} / ${preparing.total}` : ''
    };
  } else if (game.state === 'error') {
    detail = { text: game.message, error: true };
  } else if (game.state === 'exited' && game.crashed) {
    detail = { text: t('home.crashed', { code: game.code ?? '?' }), error: true };
  } else if (!target && state.releases?.error) {
    detail = { text: state.releases.error, error: true };
  } else if (target) {
    detail = { text: `${target.displayName} · Medirian ${state.releases?.manifest?.client.version ?? ''}` };
  }

  return (
    <div className="launchbar-wrap px-shadow">
      <section className="launchbar px-frame">
        <ProfilePicker profile={profile} disabled={busy} />

        <div className="launchbar__status">
          <div className="launchbar__headline pixel pixel-shadow">
            {game.state === 'running' && <span className="live-dot" />}
            {headline}
            <LiveStatus />
          </div>
          {preparing && (
            <div className="xpbar px-inset">
              <div className="xpbar__fill" style={{ width: `${progressValue}%` }} />
            </div>
          )}
          {detail && <div className={`launchbar__detail${detail.error ? ' launchbar__detail--error' : ''}`}>{detail.text}</div>}
        </div>

        <div className="launchbar__actions">
          <button className={`btn btn--icon${state.logOpen ? ' btn--pressed' : ''}`} onClick={() => state.setLogOpen(!state.logOpen)} title={t('home.log')}>
            <Icon name="terminal" size={16} />
          </button>
          {game.state === 'running' ? (
            <button className="btn btn--danger play" onClick={() => void invoke('game:kill')}>
              <Icon name="stop" size={24} />
              {t('home.stop')}
            </button>
          ) : (
            <button className="btn btn--primary play" disabled={busy || !target || !profile} onClick={onPlay}>
              {preparing ? <span className="spinner" /> : <Icon name="play" size={24} />}
              {state.account || busy ? t('home.play') : t('home.signIn')}
            </button>
          )}
        </div>
      </section>
    </div>
  );
}

/** What the running game is doing, from the launcher bridge. */
function LiveStatus() {
  const t = useT();
  const game = useStore((s) => s.game);
  if (game.state !== 'running') {
    return null;
  }
  const bridge = game.bridge;
  let text: string | null = null;
  if (bridge.state === 'multiplayer' && bridge.server) {
    text = t('status.multiplayer', { server: bridge.server });
  } else if (bridge.state === 'singleplayer') {
    text = t('status.singleplayer');
  } else if (bridge.state === 'menu') {
    text = t('status.menu');
  }
  return text ? <span className="chip chip--muted live-chip">{text}</span> : null;
}

/** The selected profile; opens a list of all profiles above the bar. */
function ProfilePicker({ profile, disabled }: { profile: LaunchProfile | undefined; disabled: boolean }) {
  const t = useT();
  const profiles = useStore((s) => s.profiles);
  const targets = useStore((s) => s.releases?.manifest?.targets) ?? NO_TARGETS;
  const selectProfile = useStore((s) => s.selectProfile);
  const navigate = useStore((s) => s.navigate);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [open]);

  const describe = (p: LaunchProfile) => {
    const target = targets.find((x) => x.id === p.targetId);
    return `Minecraft ${target?.minecraftVersion ?? p.targetId} · ${(p.memoryMb / 1024).toFixed(1).replace(/\.0$/, '')} GB`;
  };

  return (
    <div className="picker" ref={ref}>
      {open && (
        <div className="picker__menu px-frame">
          {profiles.map((p) => (
            <button key={p.id} className={`picker__option${p.id === profile?.id ? ' picker__option--active' : ''}`}
              onClick={() => {
                void selectProfile(p.id);
                setOpen(false);
              }}>
              <span className="slot px-inset"><ArtIcon name={targetIcon(targets.find((x) => x.id === p.targetId))} scale={1} /></span>
              <span className="picker__text">
                <span className="picker__name pixel">{p.name}</span>
                <span className="picker__meta">{describe(p)}</span>
              </span>
            </button>
          ))}
          <button className="picker__manage" onClick={() => navigate('profiles')}>
            <Icon name="gear" size={16} />
            {t('home.manageProfiles')}
          </button>
        </div>
      )}
      <button className="picker__current" disabled={disabled} onClick={() => setOpen(!open)}>
        <span className="slot slot--big px-inset">
          <ArtIcon name={targetIcon(targets.find((x) => x.id === profile?.targetId))} scale={2} />
        </span>
        <span className="picker__text">
          <span className="picker__label">{t('status.profile')}</span>
          <span className="picker__name pixel pixel-shadow">{profile?.name ?? t('home.noProfile')}</span>
          <span className="picker__meta">{profile ? describe(profile) : ''}</span>
        </span>
        <Icon name="chevron" size={16} className={open ? 'picker__chevron picker__chevron--open' : 'picker__chevron'} />
      </button>
    </div>
  );
}
