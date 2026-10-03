import { useEffect, useState } from 'react';
import mark from '../../assets/mark.png';
import type { SetupCheck } from '../../../common/types';
import { errorMessage, invoke } from '../api';
import { useT, type MessageKey } from '../i18n';
import { useStore } from '../store';
import { Icon } from './Icon';

const ORDER: SetupCheck['id'][] = ['system', 'memory', 'disk', 'network', 'directory', 'java', 'minecraft', 'config', 'integrity'];

/**
 * First-run setup: checks requirements, Java, Minecraft, the Meridian folder, configuration and
 * the release channel; offers automatic fixes for problems it can solve.
 */
export function SetupWizard() {
  const t = useT();
  const [checks, setChecks] = useState<SetupCheck[] | null>(null);
  const [working, setWorking] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = async (fix?: SetupCheck['id']) => {
    setWorking(fix ?? 'all');
    setError(null);
    try {
      setChecks(fix ? await invoke('setup:fix', fix) : await invoke('setup:run'));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setWorking(null);
    }
  };

  useEffect(() => {
    void run();
  }, []);

  const finish = async () => {
    await useStore.getState().updateSettings({ setupCompleted: true });
    await useStore.getState().refreshReleases(true);
  };

  const hasErrors = checks?.some((c) => c.status === 'error') ?? false;
  const sorted = checks ? [...checks].sort((a, b) => ORDER.indexOf(a.id) - ORDER.indexOf(b.id)) : [];

  return (
    <div className="overlay setup">
      <div className="setup__panel">
        <div className="setup__intro">
          <img src={mark} alt="" className="setup__mark" />
          <h2 className="dialog__title">{t('setup.title')}</h2>
          <p className="dim">{t('setup.subtitle')}</p>
        </div>
        <ul className="checks">
          {(checks ? sorted : ORDER.map((id) => ({ id, status: 'pending', detail: t('setup.running') }) as SetupCheck)).map((check) => (
            <li key={check.id} className={`check check--${check.status}`}>
              <span className="check__icon">
                {check.status === 'pending' ? <span className="spinner" /> :
                  <Icon name={check.status === 'ok' ? 'check' : check.status === 'warn' ? 'alert' : 'x'} size={14} />}
              </span>
              <div className="check__text">
                <div className="check__label">{t(`setup.check.${check.id}` as MessageKey)}</div>
                <div className="check__detail">{check.detail}</div>
              </div>
              {check.fix && (
                <button className="btn btn--small" disabled={working !== null} onClick={() => void run(check.id)}>
                  {working === check.id ? <span className="spinner" /> : <Icon name={check.fix === 'retry' ? 'refresh' : 'shield'} size={14} />}
                  {check.fix === 'retry' ? t('setup.retry') : t('setup.fix')}
                </button>
              )}
            </li>
          ))}
        </ul>
        {error && <div className="alert alert--error">{error}</div>}
        <div className="dialog__actions">
          <button className="btn btn--ghost" disabled={working !== null} onClick={() => void run()}>
            <Icon name="refresh" size={15} />
            {t('setup.retry')}
          </button>
          <button className={`btn ${hasErrors ? '' : 'btn--primary'}`} disabled={!checks || working !== null} onClick={() => void finish()}>
            {hasErrors ? t('setup.continueAnyway') : t('setup.continue')}
          </button>
        </div>
      </div>
    </div>
  );
}
