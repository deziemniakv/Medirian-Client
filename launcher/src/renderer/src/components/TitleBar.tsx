import { invoke } from '../api';
import { useT } from '../i18n';
import { useStore } from '../store';
import { Icon } from './Icon';

/** Frameless window controls (macOS keeps its native traffic lights). */
export function TitleBar() {
  const t = useT();
  const platform = useStore((s) => s.app?.platform);
  const update = useStore((s) => s.launcherUpdate);
  return (
    <div className="titlebar">
      {update.state === 'ready' && (
        <button className="titlebar__update" onClick={() => void invoke('launcherUpdate:install')}>
          <Icon name="refresh" size={14} />
          {t('launcherUpdate.restart', { version: update.version ?? '' })}
        </button>
      )}
      {platform !== 'darwin' && (
        <>
          <button className="titlebar__button" aria-label="Minimize" onClick={() => void invoke('window:minimize')}>
            <Icon name="minimize" size={16} />
          </button>
          <button className="titlebar__button titlebar__button--close" aria-label="Close" onClick={() => void invoke('window:close')}>
            <Icon name="close" size={16} />
          </button>
        </>
      )}
    </div>
  );
}
