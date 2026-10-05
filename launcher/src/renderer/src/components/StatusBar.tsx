import { useT } from '../i18n';
import { selectedProfile, useStore } from '../store';

/** Bottom bar: version, profile, RAM and live status. */
export function StatusBar() {
  const t = useT();
  const state = useStore();
  const profile = selectedProfile(state);
  const version = state.releases?.manifest?.client.version ?? '—';
  const game = state.game;

  let status = t('status.idle');
  if (game.state === 'preparing') {
    status = t('status.preparing');
  } else if (game.state === 'error') {
    status = t('status.error');
  } else if (game.state === 'running') {
    const bridge = game.bridge;
    if (bridge.state === 'multiplayer' && bridge.server) {
      status = t('status.multiplayer', { server: bridge.server });
    } else if (bridge.state === 'singleplayer') {
      status = t('status.singleplayer');
    } else if (bridge.state === 'menu') {
      status = t('status.menu');
    } else {
      status = t('status.running');
    }
  } else if (state.releases?.error && state.releases.manifest) {
    status = t('status.offline');
  }

  return (
    <footer className="statusbar">
      <span>
        <b>{t('status.version')}</b> Medirian {version}
      </span>
      <span>
        <b>{t('status.profile')}</b> {profile?.name ?? t('home.noProfile')}
      </span>
      <span>
        <b>{t('status.ram')}</b> {profile ? `${(profile.memoryMb / 1024).toFixed(1)} GB` : '—'}
      </span>
      <span className="statusbar__status">
        <i className={`dot dot--${game.state}`} />
        <b>{t('status.status')}</b> {status}
      </span>
    </footer>
  );
}
