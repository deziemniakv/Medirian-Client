import { Icon } from '../components/Icon';
import { LaunchBar } from '../components/LaunchBar';
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

export function Home() {
  const t = useT();
  const changelog = useStore((s) => s.changelog);
  const releases = useStore((s) => s.releases);
  const account = useStore((s) => s.account);
  const navigate = useStore((s) => s.navigate);
  const logOpen = useStore((s) => s.logOpen);
  const latest = changelog[0];
  const version = releases?.manifest?.client.version ?? latest?.version;
  const part = partOfDay(new Date().getHours());

  return (
    <div className="home">
      <section className="home__top">
        <div className="greeting">
          <h1 className="greeting__title pixel">
            {t(`home.greeting.${part}` as MessageKey, { name: account?.name ?? t('home.traveller') })}
          </h1>
          <p className="greeting__line">{t('home.tagline')}</p>
          <button className="player-stage" onClick={() => useStore.getState().setAccountDialog(true)} title={t('skin.title')}>
            <Skin3D scale={5} />
            <span className="player-stage__plinth" />
          </button>
        </div>

        {latest && (
          <aside className="board px-shadow">
            <div className="board__frame">
              <div className="board__sheet">
                <div className="board__pin" />
                <div className="board__head">
                  <span className="board__label pixel">{t('home.latest')}</span>
                  {version && <span className="chip chip--pumpkin">v{version}</span>}
                </div>
                <h2 className="board__title pixel">{latest.title}</h2>
                <ul className="board__list">
                  {latest.items.slice(0, 3).map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                <button className="board__more" onClick={() => navigate('changelog')}>
                  {t('home.readMore')}
                  <Icon name="chevronRight" size={16} />
                </button>
              </div>
            </div>
          </aside>
        )}
      </section>
      <LaunchBar />
      {logOpen && <LogDrawer />}
    </div>
  );
}
