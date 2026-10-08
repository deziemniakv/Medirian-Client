import { Icon } from '../components/Icon';
import { LaunchCard } from '../components/LaunchCard';
import { LogDrawer } from '../components/LogDrawer';
import { Skin3D } from '../components/Skin';
import { useT, type MessageKey } from '../i18n';
import { useStore } from '../store';

/** Morning, afternoon, evening or night, from the local time. */
function partOfDay(hour: number): 'morning' | 'afternoon' | 'evening' | 'night' {
  if (hour >= 5 && hour < 12) {
    return 'morning';
  }
  if (hour >= 12 && hour < 18) {
    return 'afternoon';
  }
  return hour >= 18 && hour < 23 ? 'evening' : 'night';
}

/**
 * Home: the player (their skin, large, with the account under it) and the launch card. The night
 * scene is the backdrop; news is one line at the bottom.
 */
export function Home() {
  const t = useT();
  const changelog = useStore((s) => s.changelog);
  const account = useStore((s) => s.account);
  const navigate = useStore((s) => s.navigate);
  const logOpen = useStore((s) => s.logOpen);
  const motion = useStore((s) => s.settings?.sceneMotion ?? true);
  const latest = changelog[0];
  const openAccount = () => useStore.getState().setAccountDialog(true);

  return (
    <div className="home">
      <section className="player">
        <button className="player__figure" onClick={openAccount} title={t('skin.title')}>
          <Skin3D scale={9} animate={motion} />
          <span className="player__plinth" />
        </button>
        <div className="player__plate">
          <span className="player__greeting">{t(`home.hello.${partOfDay(new Date().getHours())}` as MessageKey)}</span>
          <span className="player__name pixel">{account?.name ?? t('home.traveller')}</span>
          {account ? (
            <button className="link-button player__account" onClick={openAccount}>
              {account.type === 'microsoft' ? t('account.microsoft') : t('account.offline')} · {t('home.manageAccount')}
            </button>
          ) : (
            <button className="btn btn--small player__signin" onClick={openAccount}>
              <Icon name="user" size={16} />
              {t('account.signIn')}
            </button>
          )}
        </div>
      </section>

      <LaunchCard />

      {latest && (
        <button className="news-line" onClick={() => navigate('changelog')}>
          <span className="chip chip--pumpkin">v{latest.version}</span>
          <span className="news-line__title">{latest.title}</span>
          <span className="news-line__more">{t('home.readMore')}<Icon name="chevronRight" size={16} /></span>
        </button>
      )}
      {logOpen && <LogDrawer />}
    </div>
  );
}
