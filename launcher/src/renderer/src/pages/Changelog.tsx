import { ArtIcon } from '../components/Icon';
import { useT } from '../i18n';
import { useStore } from '../store';

/** News: the changelog as journal pages, newest first. */
export function Changelog() {
  const t = useT();
  const changelog = useStore((s) => s.changelog);
  return (
    <div className="page">
      <div className="window px-frame">
        <div className="window__header">
          <ArtIcon name="news" scale={2} />
          <div>
            <h1 className="window__title">{t('news.title')}</h1>
            <p className="window__subtitle">{t('news.subtitle')}</p>
          </div>
        </div>
        <div className="window__body journal">
          {changelog.map((entry, index) => (
            <article key={entry.version} className={`entry px-frame px-frame--surface${index === 0 ? ' entry--latest' : ''}`}>
              <header className="entry__head">
                <span className={`chip${index === 0 ? ' chip--pumpkin' : ''}`}>v{entry.version}</span>
                <h2 className="entry__title pixel">{entry.title}</h2>
                <span className="entry__date">{entry.date}</span>
              </header>
              <ul className="entry__items">
                {entry.items.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </article>
          ))}
        </div>
      </div>
    </div>
  );
}
