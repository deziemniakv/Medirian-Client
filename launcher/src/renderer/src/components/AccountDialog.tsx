import { useEffect, useState } from 'react';
import type { DeviceCodeInfo } from '../../../common/types';
import { errorMessage, invoke, on } from '../api';
import { useT } from '../i18n';
import { useStore } from '../store';
import { Icon } from './Icon';
import { Skin3D } from './Skin';

/** Microsoft sign-in (device code flow), account info and the development offline account. */
export function AccountDialog() {
  const t = useT();
  const account = useStore((s) => s.account);
  const close = () => useStore.getState().setAccountDialog(false);
  const [code, setCode] = useState<DeviceCodeInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [offlineAllowed, setOfflineAllowed] = useState(false);
  const [offlineName, setOfflineName] = useState('');

  useEffect(() => {
    void invoke('account:offlineAllowed').then(setOfflineAllowed);
    return on('account:loginResult', (result) => {
      setCode(null);
      setBusy(false);
      if (result.ok) {
        close();
      } else {
        setError(result.error);
      }
    });
  }, []);

  const startLogin = async () => {
    setError(null);
    setBusy(true);
    try {
      setCode(await invoke('account:loginStart'));
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };

  const cancel = () => {
    void invoke('account:loginCancel');
    setCode(null);
    setBusy(false);
  };

  const useOffline = async () => {
    setError(null);
    try {
      await invoke('account:offline', offlineName);
      close();
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && !code && close()}>
      <div className={`dialog px-frame${code ? '' : ' dialog--wide account-dialog'}`}>
        <div className="dialog__header">
          <Icon name="user" size={16} />
          <h2 className="dialog__title">{t('account.title')}</h2>
          {!code && (
            <button className="btn btn--ghost btn--small btn--icon" aria-label={t('account.close')} onClick={close}>
              <Icon name="close" size={16} />
            </button>
          )}
        </div>
        <div className="dialog__body">

        {!code && <SkinPanel signedIn={!!account} name={account?.name ?? null} type={account?.type ?? null} />}

        {code ? (
          <>
            <p className="dim">{t('account.step')}</p>
            <div className="device-code px-inset pixel">{code.userCode}</div>
            <div className="row">
              <button className="btn btn--primary" onClick={() => void invoke('shell:openExternal', code.verificationUri)}>
                <Icon name="external" size={16} />
                {t('account.openLink')}
              </button>
              <button className="btn" onClick={() => {
                void navigator.clipboard.writeText(code.userCode);
                setCopied(true);
              }}>
                <Icon name={copied ? 'check' : 'copy'} size={16} />
                {copied ? t('account.copied') : t('account.copy')}
              </button>
            </div>
            <div className="row dim account-waiting">
              <span className="spinner" />
              {t('account.waiting')}
            </div>
          </>
        ) : (
          !account && (
            <button className="btn btn--primary account-signin" disabled={busy} onClick={() => void startLogin()}>
              {busy ? <span className="spinner" /> : <Icon name="shield" size={16} />}
              {t('account.signIn')}
            </button>
          )
        )}

        {error && <div className="alert alert--error account-error"><Icon name="alert" size={16} />{error}</div>}

        {offlineAllowed && !code && !account && (
          <div className="account-offline">
            <div className="field__label">{t('account.offlineTitle')}</div>
            <div className="field__hint">{t('account.offlineHint')}</div>
            <div className="row">
              <input className="input" placeholder={t('account.offlineName')} value={offlineName} maxLength={16}
                onChange={(e) => setOfflineName(e.target.value)} />
              <button className="btn" disabled={offlineName.trim().length < 3} onClick={() => void useOffline()}>
                {t('account.useOffline')}
              </button>
            </div>
          </div>
        )}

        </div>
        <div className="dialog__actions">
          {code && <button className="btn btn--ghost" onClick={cancel}>{t('account.close')}</button>}
          {account && !code && (
            <button className="btn btn--danger" onClick={() => void invoke('account:logout')}>{t('account.signOut')}</button>
          )}
          {!code && <button className="btn" onClick={close}>{t('account.close')}</button>}
        </div>
      </div>
    </div>
  );
}

/** The player's skin, large, next to the account and where the skin comes from. */
function SkinPanel({ signedIn, name, type }: { signedIn: boolean; name: string | null; type: 'microsoft' | 'offline' | null }) {
  const t = useT();
  const skin = useStore((s) => s.skin);
  const motion = useStore((s) => s.settings?.sceneMotion ?? true);
  const [refreshing, setRefreshing] = useState(false);
  const refresh = async () => {
    setRefreshing(true);
    try {
      await invoke('skin:refresh');
    } finally {
      setRefreshing(false);
    }
  };
  return (
    <div className="account-profile">
      <div className="account-profile__stage px-inset">
        <Skin3D scale={10} animate={motion} />
        <span className="account-profile__hint muted">{t('skin.drag')}</span>
      </div>
      <div className="account-profile__side">
        {name ? (
          <>
            <div className="account-profile__name pixel">{name}</div>
            <div className="muted">{type === 'microsoft' ? t('account.microsoft') : t('account.offline')}</div>
          </>
        ) : (
          <div className="account-profile__name pixel">{t('account.notSignedIn')}</div>
        )}
        <hr className="groove" />
        <div className="field__label">{t('skin.title')}</div>
        {skin?.source === 'mojang' ? (
          <>
            <div>{t('skin.model', { model: skin.model === 'slim' ? t('skin.slim') : t('skin.classic') })}</div>
            <div className="muted">{t('skin.updated', { time: new Date(skin.updatedAt).toLocaleTimeString() })}</div>
          </>
        ) : (
          <div className="muted">{signedIn ? t('skin.none') : t('skin.default')}</div>
        )}
        {skin?.error && <div className="skin-panel__error">{skin.error}</div>}
        {signedIn && (
          <div className="row skin-panel__actions">
            <button className="btn btn--small" disabled={refreshing} onClick={() => void refresh()}>
              {refreshing ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
              {t('skin.refresh')}
            </button>
            <button className="btn btn--small btn--ghost" onClick={() => void invoke('shell:openExternal', 'https://www.minecraft.net/msaprofile/mygames/editskin')}>
              <Icon name="external" size={16} />
              {t('skin.change')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
