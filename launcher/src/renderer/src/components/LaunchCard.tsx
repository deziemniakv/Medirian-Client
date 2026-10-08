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

const gigabytes = (mb: number) => `${(mb / 1024).toFixed(1).replace(/\.0$/, '')} GB`;

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

/**
 * The one place to start the game: which profile, what it is, whether it is ready, and Play.
 * Everything else on the home screen is secondary to this card.
 */
export function LaunchCard() {
  const t = useT();
  const state = useStore();
  const profile = selectedProfile(state);
  const target = state.releases?.manifest?.targets.find((x) => x.id === profile?.targetId);
  const status = state.statuses.find((x) => x.targetId === profile?.targetId);
  const game = state.game;
  const busy = game.state === 'preparing' || game.state === 'running';
  const preparing = game.state === 'preparing' ? game.progress : null;
  const [mods, setMods] = useState<number | null>(null);

  useEffect(() => {
    setMods(null);
    if (profile) {
      void invoke('mods:count', profile.id).then(setMods).catch(() => setMods(null));
    }
  }, [profile?.id, game.state]);

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
    detail = { text: `Medirian Client ${state.releases?.manifest?.client.version ?? ''}` };
  }

  return (
    <section className="launch-card px-frame" aria-label={t('home.play')}>
      <ProfilePicker profile={profile} disabled={busy} />

      {profile && (
        <ul className="launch-card__facts">
          <li><span className="muted">{t('home.factVersion')}</span><b>{target?.displayName ?? profile.targetId}</b></li>
          <li><span className="muted">{t('home.factLoader')}</span><b>{target ? (target.loader.type === 'legacy-fabric' ? 'Legacy Fabric' : 'Fabric') : '—'}</b></li>
          <li><span className="muted">{t('home.factMemory')}</span><b>{gigabytes(profile.memoryMb)}</b></li>
          <li><span className="muted">{t('home.factMods')}</span><b>{mods ?? '—'}</b></li>
        </ul>
      )}

      <div className="launch-card__status" aria-live="polite">
        <div className="launch-card__headline pixel">
          {game.state === 'running' && <span className="live-dot" />}
          {headline}
          <LiveStatus />
        </div>
        {preparing && (
          <div className="xpbar px-inset">
            <div className="xpbar__fill" style={{ width: `${progressValue}%` }} />
          </div>
        )}
        {detail && detail.text && <div className={`launch-card__detail${detail.error ? ' launch-card__detail--error' : ''}`}>{detail.text}</div>}
      </div>

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

      <div className="launch-card__tools">
        <button className={`btn btn--ghost btn--small${state.logOpen ? ' btn--pressed' : ''}`} onClick={() => state.setLogOpen(!state.logOpen)}>
          <Icon name="terminal" size={16} />
          {t('home.log')}
        </button>
        {profile && (
          <button className="btn btn--ghost btn--small" onClick={() => void invoke('shell:open', 'instance', profile.id)}>
            <Icon name="folder" size={16} />
            {t('home.folder')}
          </button>
        )}
      </div>
    </section>
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

/** The selected profile, large; opens the list of all profiles below it. */
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
    const escape = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('mousedown', close);
    window.addEventListener('keydown', escape);
    return () => {
      window.removeEventListener('mousedown', close);
      window.removeEventListener('keydown', escape);
    };
  }, [open]);

  const describe = (p: LaunchProfile) => {
    const target = targets.find((x) => x.id === p.targetId);
    return `Minecraft ${target?.minecraftVersion ?? p.targetId} · ${gigabytes(p.memoryMb)}`;
  };

  return (
    <div className="picker" ref={ref}>
      <span className="picker__label">{t('status.profile')}</span>
      <button className="picker__current" disabled={disabled} aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="slot slot--big px-inset">
          <ArtIcon name={targetIcon(targets.find((x) => x.id === profile?.targetId))} scale={2} />
        </span>
        <span className="picker__name pixel">{profile?.name ?? t('home.noProfile')}</span>
        <Icon name="chevron" size={16} className={open ? 'picker__chevron picker__chevron--open' : 'picker__chevron'} />
      </button>
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
                <span className="picker__option-name pixel">{p.name}</span>
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
    </div>
  );
}
