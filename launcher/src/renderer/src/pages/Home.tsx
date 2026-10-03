import mark from '../../assets/mark.png';
import { Atmosphere } from '../components/Atmosphere';
import { Icon } from '../components/Icon';
import { LaunchPanel } from '../components/LaunchPanel';
import { LogDrawer } from '../components/LogDrawer';
import { StatusBar } from '../components/StatusBar';
import { useT } from '../i18n';
import { useStore } from '../store';

export function Home() {
  const t = useT();
  const changelog = useStore((s) => s.changelog);
  const releases = useStore((s) => s.releases);
  const navigate = useStore((s) => s.navigate);
  const logOpen = useStore((s) => s.logOpen);
  const latest = changelog[0];
  const version = releases?.manifest?.client.version ?? latest?.version;

  return (
    <div className="home">
      <Atmosphere />
      <section className="hero">
        <div className="hero__brand">
          <span className="chip chip--seasonal hero__eyebrow">{t('home.eyebrow')}</span>
          <img className="hero__mark" src={mark} alt="" draggable={false} />
          <h1 className="hero__wordmark">MERIDIAN</h1>
          <div className="hero__client">CLIENT</div>
          {latest && (
            <p className="hero__tagline">
              {latest.title}
              {version && <span className="hero__version">v{version}</span>}
            </p>
          )}
        </div>
        {latest && (
          <aside className="news">
            <div className="news__header">
              <span className="news__label">{t('home.latest')}</span>
              <span className="news__date">{latest.date}</span>
            </div>
            <ul className="news__list">
              {latest.items.slice(0, 4).map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
            <button className="btn btn--ghost btn--small news__more" onClick={() => navigate('changelog')}>
              {t('home.readMore')}
              <Icon name="chevron" size={14} />
            </button>
          </aside>
        )}
      </section>
      <LaunchPanel />
      <StatusBar />
      {logOpen && <LogDrawer />}
    </div>
  );
}
