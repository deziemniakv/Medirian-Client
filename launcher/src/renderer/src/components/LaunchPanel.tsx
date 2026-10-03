import { invoke } from '../api';
import { useT, type MessageKey } from '../i18n';
import { selectedProfile, useStore } from '../store';
import { Icon } from './Icon';

function percent(done: number, total: number): number {
  return total > 0 ? Math.min(100, Math.round((done / total) * 100)) : 0;
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) {
    return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
  }
  return `${Math.round(bytes / 1024 ** 2)} MB`;
}

/** Profile selection, readiness status and the PLAY button. */
export function LaunchPanel() {
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

  return (
    <section className="launch">
      <div className="launch__target">
        <span className="launch__eyebrow">{target ? `Minecraft ${target.minecraftVersion}` : profile?.targetId ?? ''}</span>
        <span className="launch__title">Meridian Client</span>
        <select
          className="select launch__profile"
          value={profile?.id ?? ''}
          disabled={busy}
          onChange={(e) => void state.selectProfile(e.target.value)}
        >
          {state.profiles.map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>

      <div className="launch__status">
        <div className={`launch__headline${game.state === 'running' ? ' launch__headline--live' : ''}`}>
          {game.state === 'running' && <span className="pulse" />}
          {headline}
        </div>
        {preparing ? (
          <>
            <div className="progress">
              <div className="progress__bar" style={{ width: `${progressValue}%` }} />
            </div>
            <div className="launch__detail">
              {preparing.bytesTotal
                ? `${formatBytes(preparing.bytesDone ?? 0)} / ${formatBytes(preparing.bytesTotal)} · ${progressValue}%`
                : preparing.total > 1 ? `${preparing.done} / ${preparing.total}` : ''}
            </div>
          </>
        ) : game.state === 'error' ? (
          <div className="launch__detail launch__detail--error">{game.message}</div>
        ) : game.state === 'exited' && game.crashed ? (
          <div className="launch__detail launch__detail--error">
            {t('home.crashed', { code: game.code ?? '?' })}
          </div>
        ) : !target && state.releases?.error ? (
          <div className="launch__detail launch__detail--error">{state.releases.error}</div>
        ) : (
          <div className="launch__detail">
            {target ? `${target.displayName} · Meridian ${state.releases?.manifest?.client.version ?? ''}` : ''}
          </div>
        )}
      </div>

      <div className="launch__actions">
        <button className="btn btn--ghost launch__log" onClick={() => state.setLogOpen(!state.logOpen)} title={t('home.log')}>
          <Icon name="terminal" size={18} />
        </button>
        {game.state === 'running' ? (
          <button className="play play--stop" onClick={() => void invoke('game:kill')}>
            <Icon name="stop" size={18} />
            {t('home.stop')}
          </button>
        ) : (
          <button className="play" disabled={busy || !target || !profile} onClick={onPlay}>
            {preparing ? <span className="spinner" /> : <Icon name="play" size={20} />}
            {state.account || busy ? t('home.play') : t('home.signIn')}
          </button>
        )}
      </div>
    </section>
  );
}
