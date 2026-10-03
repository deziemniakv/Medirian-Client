import { useT } from '../i18n';
import { useStore } from '../store';

export function Changelog() {
  const t = useT();
  const changelog = useStore((s) => s.changelog);
  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">{t('news.title')}</h1>
          <p className="page__subtitle">{t('news.subtitle')}</p>
        </div>
      </div>
      <div className="timeline">
        {changelog.map((entry, index) => (
          <article key={entry.version} className="timeline__entry">
            <div className="timeline__marker">
              <span className={`timeline__dot${index === 0 ? ' timeline__dot--latest' : ''}`} />
            </div>
            <div className="card timeline__card">
              <div className="row row--between">
                <h2 className="timeline__title">
                  <span className="chip">v{entry.version}</span> {entry.title}
                </h2>
                <span className="muted">{entry.date}</span>
              </div>
              <ul className="timeline__items">
                {entry.items.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
