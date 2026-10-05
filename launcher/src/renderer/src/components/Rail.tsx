import mark from '../../assets/mark.png';
import { useT } from '../i18n';
import { useStore, type Page } from '../store';
import { Icon, type IconName } from './Icon';

const ITEMS: { page: Page; icon: IconName; label: 'nav.play' | 'nav.profiles' | 'nav.news' | 'nav.settings' }[] = [
  { page: 'home', icon: 'play', label: 'nav.play' },
  { page: 'profiles', icon: 'layers', label: 'nav.profiles' },
  { page: 'changelog', icon: 'book', label: 'nav.news' },
  { page: 'settings', icon: 'settings', label: 'nav.settings' }
];

/** Left navigation rail with the Medirian mark and the account button. */
export function Rail() {
  const t = useT();
  const page = useStore((s) => s.page);
  const navigate = useStore((s) => s.navigate);
  const account = useStore((s) => s.account);
  const openAccount = useStore((s) => s.setAccountDialog);

  return (
    <nav className="rail">
      <img className="rail__logo" src={mark} alt="Medirian" draggable={false} />
      <div className="rail__nav">
        {ITEMS.map((item) => (
          <button
            key={item.page}
            className={`rail__item${page === item.page ? ' rail__item--active' : ''}`}
            onClick={() => navigate(item.page)}
          >
            <Icon name={item.icon} size={20} />
            {t(item.label)}
          </button>
        ))}
      </div>
      <div className="rail__spacer" />
      <button className="rail__account" title={account ? account.name : t('account.notSignedIn')} onClick={() => openAccount(true)}>
        {account ? account.name.slice(0, 1).toUpperCase() : <Icon name="user" size={18} />}
      </button>
    </nav>
  );
}
