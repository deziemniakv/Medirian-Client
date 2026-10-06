import mark from '../../assets/mark.png';
import { invoke } from '../api';
import { useT, type MessageKey } from '../i18n';
import { useStore, type Page } from '../store';
import { Icon, type IconName } from './Icon';

const NAV: { page: Page; icon: IconName; label: MessageKey }[] = [
  { page: 'home', icon: 'play', label: 'nav.play' },
  { page: 'profiles', icon: 'user', label: 'nav.profiles' },
  { page: 'changelog', icon: 'book', label: 'nav.news' },
  { page: 'settings', icon: 'gear', label: 'nav.settings' }
];

/**
 * The bar across the top: the Medirian Client wordmark, the navigation, the account and the
 * window controls (macOS keeps its traffic lights). The whole bar drags the window.
 */
export function TopBar() {
  const t = useT();
  const page = useStore((s) => s.page);
  const navigate = useStore((s) => s.navigate);
  const account = useStore((s) => s.account);
  const openAccount = useStore((s) => s.setAccountDialog);
  const platform = useStore((s) => s.app?.platform);
  const update = useStore((s) => s.launcherUpdate);

  return (
    <header className={`topbar${platform === 'darwin' ? ' topbar--mac' : ''}`}>
      <div className="brand">
        <img className="brand__mark" src={mark} alt="" draggable={false} />
        <div className="brand__word pixel">
          <span className="brand__name">MEDIRIAN</span>
          <span className="brand__client">CLIENT</span>
        </div>
      </div>

      <nav className="nav">
        {NAV.map((item) => (
          <button key={item.page} className={`nav__item pixel${page === item.page ? ' nav__item--active' : ''}`}
            onClick={() => navigate(item.page)}>
            <Icon name={item.icon} size={16} />
            {t(item.label)}
          </button>
        ))}
      </nav>

      <div className="topbar__right">
        {update.state === 'ready' && (
          <button className="btn btn--primary btn--small" onClick={() => void invoke('launcherUpdate:install')}>
            <Icon name="refresh" size={16} />
            {t('launcherUpdate.restart', { version: update.version ?? '' })}
          </button>
        )}
        <button className="account-chip" title={account ? account.name : t('account.notSignedIn')} onClick={() => openAccount(true)}>
          <span className="account-chip__avatar pixel">{account ? account.name.slice(0, 1).toUpperCase() : <Icon name="user" size={16} />}</span>
          <span className="account-chip__name">{account ? account.name : t('account.signIn')}</span>
        </button>
        {platform !== 'darwin' && (
          <div className="window-controls">
            <button className="window-btn" aria-label="Minimize" onClick={() => void invoke('window:minimize')}>
              <Icon name="minimize" size={16} />
            </button>
            <button className="window-btn window-btn--close" aria-label="Close" onClick={() => void invoke('window:close')}>
              <Icon name="close" size={16} />
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
