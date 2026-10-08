import { useEffect, useState } from 'react';
import type { LaunchProfile, ShareError, ShareExport, SharePreview } from '../../../common/types';
import { invoke } from '../api';
import { useT, type MessageKey } from '../i18n';
import { useStore } from '../store';
import { Icon } from './Icon';

const errorText = (t: ReturnType<typeof useT>, error: ShareError) => t(`share.error.${error}` as MessageKey);

function Dialog({ icon, title, onClose, children, actions }: {
  icon: 'share' | 'download';
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  actions: React.ReactNode;
}) {
  const t = useT();
  useEffect(() => {
    const escape = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', escape);
    return () => window.removeEventListener('keydown', escape);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="dialog px-frame share" role="dialog" aria-modal="true" aria-label={title}>
        <div className="dialog__header">
          <Icon name={icon} size={16} />
          <h2 className="dialog__title">{title}</h2>
          <button className="btn btn--ghost btn--small btn--icon" aria-label={t('account.close')} onClick={onClose}>
            <Icon name="close" size={16} />
          </button>
        </div>
        <div className="dialog__body">{children}</div>
        <div className="dialog__actions">{actions}</div>
      </div>
    </div>
  );
}

/** Turns a profile into a code: creates it right away and shows it with Copy and Delete. */
export function ShareDialog({ profile, onClose }: { profile: LaunchProfile; onClose: () => void }) {
  const t = useT();
  const [result, setResult] = useState<ShareExport | null>(null);
  const [error, setError] = useState<ShareError | null>(null);
  const [copied, setCopied] = useState(false);
  const [deleted, setDeleted] = useState(false);
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let current = true;
    void invoke('share:export', profile.id).then((r) => {
      if (!current) {
        return;
      }
      setBusy(false);
      if (r.ok) {
        setResult(r.value);
      } else {
        setError(r.error);
      }
    });
    return () => {
      current = false;
    };
  }, [profile.id]);

  const copy = async () => {
    if (result) {
      await navigator.clipboard.writeText(result.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    }
  };

  const remove = async () => {
    if (!result) {
      return;
    }
    setBusy(true);
    const r = await invoke('share:delete', result.code);
    setBusy(false);
    if (r.ok) {
      setDeleted(true);
    } else {
      setError(r.error);
    }
  };

  return (
    <Dialog icon="share" title={t('share.exportTitle', { name: profile.name })} onClose={onClose}
      actions={
        <>
          {result && !deleted && (
            <button className="btn btn--ghost" disabled={busy} onClick={() => void remove()}>
              <Icon name="trash" size={16} />{t('share.delete')}
            </button>
          )}
          <button className="btn" onClick={onClose}>{t('account.close')}</button>
          {result && !deleted && (
            <button className="btn btn--primary" onClick={() => void copy()}>
              <Icon name={copied ? 'check' : 'copy'} size={16} />{copied ? t('account.copied') : t('share.copy')}
            </button>
          )}
        </>
      }>
      {busy && !result && <div className="share__state"><span className="spinner" /><span>{t('share.creating')}</span></div>}
      {error && <div className="alert alert--error">{errorText(t, error)}</div>}
      {result && !deleted && (
        <>
          <p className="dim">{t('share.exportHint')}</p>
          <button className="share__code pixel" onClick={() => void copy()} title={t('share.copy')}>{result.code}</button>
          <p className="muted share__note">{t('share.expires', { date: new Date(result.expiresAt).toLocaleDateString() })}</p>
          <p className="muted share__note">{t('share.contains')}</p>
        </>
      )}
      {deleted && <div className="alert">{t('share.deleted')}</div>}
    </Dialog>
  );
}

/** Looks a code up, shows what it brings and imports it after confirmation. */
export function ImportDialog({ onClose, onImported }: { onClose: () => void; onImported: (profile: LaunchProfile) => void }) {
  const t = useT();
  const profiles = useStore((s) => s.profiles);
  const targets = useStore((s) => s.releases?.manifest?.targets);
  const [code, setCode] = useState('');
  const [preview, setPreview] = useState<SharePreview | null>(null);
  const [error, setError] = useState<ShareError | null>(null);
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState<'new' | 'replace'>('new');
  const [target, setTarget] = useState<string>('');
  const [withClient, setWithClient] = useState(false);

  const lookUp = async (value = code) => {
    setBusy(true);
    setError(null);
    setPreview(null);
    const r = await invoke('share:preview', value);
    setBusy(false);
    if (r.ok) {
      setPreview(r.value);
      setMode(r.value.existingProfileId ? 'replace' : 'new');
      setTarget(r.value.existingProfileId ?? profiles[0]?.id ?? '');
    } else {
      setError(r.error);
    }
  };

  const paste = async () => {
    const text = (await navigator.clipboard.readText()).trim().slice(0, 40);
    setCode(text);
    void lookUp(text);
  };

  const apply = async () => {
    if (!preview) {
      return;
    }
    setBusy(true);
    const r = await invoke('share:apply', preview.code, mode, mode === 'replace' ? target : null, withClient);
    setBusy(false);
    if (r.ok) {
      onImported(r.value);
    } else {
      setError(r.error);
    }
  };

  const replaced = profiles.find((p) => p.id === target);
  const version = targets?.find((x) => x.id === preview?.targetId)?.displayName ?? preview?.targetId;

  return (
    <Dialog icon="download" title={t('share.importTitle')} onClose={onClose}
      actions={
        <>
          <button className="btn" onClick={onClose}>{t('mods.cancel')}</button>
          {preview ? (
            <button className={`btn ${mode === 'replace' ? 'btn--danger' : 'btn--primary'}`} disabled={busy || (mode === 'replace' && !replaced)} onClick={() => void apply()}>
              {busy ? <span className="spinner" /> : <Icon name="download" size={16} />}
              {mode === 'replace' ? t('share.replaceConfirm', { name: replaced?.name ?? '' }) : t('share.importNew')}
            </button>
          ) : (
            <button className="btn btn--primary" disabled={busy || code.trim().length < 12} onClick={() => void lookUp()}>
              {busy ? <span className="spinner" /> : <Icon name="search" size={16} />}{t('share.lookUp')}
            </button>
          )}
        </>
      }>
      <p className="dim">{t('share.importHint')}</p>
      <div className="row share__input">
        <input className="input mono" value={code} placeholder="MDN-XXXX-XXXX-XXXX" spellCheck={false} autoFocus maxLength={40}
          aria-label={t('share.codeLabel')}
          onChange={(e) => { setCode(e.target.value); setPreview(null); setError(null); }}
          onKeyDown={(e) => e.key === 'Enter' && code.trim().length >= 12 && void lookUp()} />
        <button className="btn" onClick={() => void paste()}><Icon name="copy" size={16} />{t('share.paste')}</button>
      </div>
      {busy && !preview && <div className="share__state"><span className="spinner" /><span>{t('share.looking')}</span></div>}
      {error && <div className="alert alert--error">{errorText(t, error)}</div>}
      {preview && (
        <>
          <div className="share__preview px-inset">
            <div className="pixel share__name">{preview.name}</div>
            <ul className="share__facts">
              <li>{version} · {(preview.memoryMb / 1024).toFixed(1).replace(/\.0$/, '')} GB</li>
              <li>{t('share.modules', { enabled: preview.enabledModules, total: preview.modules })}</li>
              {preview.gameOptions > 0 && <li>{t('share.gameOptions', { count: preview.gameOptions })}</li>}
              <li className="muted">{t('share.expires', { date: new Date(preview.expiresAt).toLocaleDateString() })}</li>
            </ul>
          </div>
          <div className="share__modes" role="radiogroup">
            <label className={`share__mode${mode === 'new' ? ' share__mode--on' : ''}`}>
              <input type="radio" name="mode" checked={mode === 'new'} onChange={() => setMode('new')} />
              <span><b>{t('share.modeNew')}</b><span className="muted">{t('share.modeNewHint')}</span></span>
            </label>
            <label className={`share__mode${mode === 'replace' ? ' share__mode--on' : ''}`}>
              <input type="radio" name="mode" checked={mode === 'replace'} onChange={() => setMode('replace')} />
              <span>
                <b>{t('share.modeReplace')}</b>
                <span className="muted">{t('share.modeReplaceHint')}</span>
                {mode === 'replace' && (
                  <select className="select" value={target} onChange={(e) => setTarget(e.target.value)}>
                    {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                )}
              </span>
            </label>
          </div>
          {preview.hasClientSettings && (
            <label className="share__check">
              <input type="checkbox" checked={withClient} onChange={(e) => setWithClient(e.target.checked)} />
              <span>{t('share.withClient')}</span>
            </label>
          )}
          {mode === 'replace' && replaced && <div className="alert alert--warn">{t('share.replaceWarning', { name: replaced.name })}</div>}
        </>
      )}
    </Dialog>
  );
}
