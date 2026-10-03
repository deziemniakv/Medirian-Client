import { useEffect, useRef } from 'react';
import { invoke } from '../api';
import { useT } from '../i18n';
import { useStore } from '../store';
import { Icon } from './Icon';

/** Live game output (Minecraft stdout/stderr), newest at the bottom. */
export function LogDrawer() {
  const t = useT();
  const log = useStore((s) => s.log);
  const close = useStore((s) => s.setLogOpen);
  const body = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = body.current;
    if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 80) {
      el.scrollTop = el.scrollHeight;
    }
  }, [log]);

  return (
    <div className="drawer">
      <div className="drawer__header">
        <span className="drawer__title">
          <Icon name="terminal" size={16} /> {t('home.log')}
        </span>
        <div className="row">
          <button className="btn btn--ghost btn--small" onClick={() => void navigator.clipboard.writeText(log.join('\n'))}>
            <Icon name="copy" size={14} />
          </button>
          <button className="btn btn--ghost btn--small" onClick={() => void invoke('shell:open', 'logs')}>
            <Icon name="folder" size={14} />
          </button>
          <button className="btn btn--ghost btn--small" onClick={() => close(false)}>
            <Icon name="x" size={14} />
          </button>
        </div>
      </div>
      <div className="drawer__body mono" ref={body}>
        {log.length === 0 ? <span className="muted">—</span> : log.map((line, i) => (
          <div key={i} className={/ERROR|Exception|FATAL/.test(line) ? 'log-line log-line--error' : /WARN/.test(line) ? 'log-line log-line--warn' : 'log-line'}>
            {line}
          </div>
        ))}
      </div>
    </div>
  );
}
