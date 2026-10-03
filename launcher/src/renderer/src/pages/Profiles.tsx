import { useEffect, useMemo, useState } from 'react';
import type { JavaInstall, LaunchProfile } from '../../../common/types';
import { errorMessage, invoke } from '../api';
import { Icon } from '../components/Icon';
import { useT } from '../i18n';
import { useStore } from '../store';

function same(a: LaunchProfile, b: LaunchProfile): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

export function Profiles() {
  const t = useT();
  const profiles = useStore((s) => s.profiles);
  const settings = useStore((s) => s.settings);
  const releases = useStore((s) => s.releases);
  const system = useStore((s) => s.system);
  const setProfiles = useStore((s) => s.setProfiles);
  const selectProfile = useStore((s) => s.selectProfile);
  const [selectedId, setSelectedId] = useState<string>(settings?.selectedProfileId ?? profiles[0]?.id ?? '');
  const selected = profiles.find((p) => p.id === selectedId) ?? profiles[0];
  const [draft, setDraft] = useState<LaunchProfile | undefined>(selected);
  const [javas, setJavas] = useState<JavaInstall[]>([]);
  const [configs, setConfigs] = useState<string[]>([]);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => setDraft(selected), [selected?.id]);
  useEffect(() => {
    void invoke('java:detect').then(setJavas);
    void invoke('client:configProfiles').then(setConfigs);
  }, []);

  const targets = releases?.manifest?.targets ?? [];
  const maxMemory = useMemo(() => {
    const total = system?.totalMemoryMb ?? 8192;
    return Math.max(2048, Math.min(16384, Math.floor((total - 2048) / 256) * 256));
  }, [system]);

  if (!draft) {
    return null;
  }
  const dirty = selected ? !same(draft, selected) : false;
  const patch = (changes: Partial<LaunchProfile>) => {
    setDraft({ ...draft, ...changes });
    setSaved(false);
  };

  const save = async () => {
    setError(null);
    try {
      setProfiles(await invoke('profiles:save', draft));
      setSaved(true);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const create = async (base: Partial<LaunchProfile>) => {
    const created = await invoke('profiles:create', base);
    setProfiles(await invoke('profiles:list'));
    setSelectedId(created.id);
  };

  const remove = async () => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }
    try {
      const next = await invoke('profiles:delete', draft.id);
      setProfiles(next);
      setSelectedId(next[0].id);
      setConfirmDelete(false);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const javaOptions = javas.filter((java) => {
    const target = targets.find((x) => x.id === draft.targetId);
    if (!target) {
      return true;
    }
    return target.branch === 'legacy' ? java.major === 8 : java.major >= target.java.majorVersion;
  });

  return (
    <div className="page">
      <div className="page__header">
        <div>
          <h1 className="page__title">{t('profiles.title')}</h1>
          <p className="page__subtitle">{t('profiles.subtitle')}</p>
        </div>
        <button className="btn btn--primary" onClick={() => void create({ name: t('profiles.new'), targetId: targets[0]?.id ?? '1.8.9' })}>
          <Icon name="plus" size={16} />
          {t('profiles.new')}
        </button>
      </div>

      <div className="profiles">
        <div className="profile-list">
          {profiles.map((p) => {
            const target = targets.find((x) => x.id === p.targetId);
            return (
              <button key={p.id} className={`profile-item${p.id === draft.id ? ' profile-item--active' : ''}`}
                onClick={() => {
                  setSelectedId(p.id);
                  setConfirmDelete(false);
                  void selectProfile(p.id);
                }}>
                <span className={`profile-item__badge profile-item__badge--${target?.branch ?? 'modern'}`}>{p.targetId}</span>
                <span className="profile-item__name">{p.name}</span>
                <span className="profile-item__meta">
                  {p.lastPlayed ? t('profiles.lastPlayed', { date: new Date(p.lastPlayed).toLocaleDateString() }) : t('profiles.neverPlayed')}
                </span>
              </button>
            );
          })}
        </div>

        <div className="card profile-editor">
          <div className="profile-editor__grid">
            <label className="field">
              <span className="field__label">{t('profiles.name')}</span>
              <input className="input" value={draft.name} maxLength={40} onChange={(e) => patch({ name: e.target.value })} />
            </label>
            <label className="field">
              <span className="field__label">{t('profiles.version')}</span>
              <select className="select" value={draft.targetId} onChange={(e) => patch({ targetId: e.target.value, javaPath: null })}>
                {targets.length === 0 && <option value={draft.targetId}>{draft.targetId}</option>}
                {targets.map((target) => (
                  <option key={target.id} value={target.id}>
                    {target.displayName}{target.description ? ` — ${target.description}` : ''}
                  </option>
                ))}
              </select>
            </label>

            <div className="field field--wide">
              <span className="field__label">
                {t('profiles.memory')} <b className="memory-value">{(draft.memoryMb / 1024).toFixed(2).replace(/\.?0+$/, '')} GB</b>
              </span>
              <input className="range" type="range" min={1024} max={maxMemory} step={256} value={Math.min(draft.memoryMb, maxMemory)}
                onChange={(e) => patch({ memoryMb: Number(e.target.value) })} />
              <span className="field__hint">{t('profiles.memoryHint', { total: Math.round((system?.totalMemoryMb ?? 0) / 1024) })}</span>
            </div>

            <label className="field field--wide">
              <span className="field__label">{t('profiles.java')}</span>
              <select className="select" value={draft.javaPath ?? ''} onChange={(e) => patch({ javaPath: e.target.value || null })}>
                <option value="">{t('profiles.javaAuto')}</option>
                {javaOptions.map((java) => (
                  <option key={java.path} value={java.path}>Java {java.version} · {java.vendor || java.arch} — {java.path}</option>
                ))}
                {draft.javaPath && !javaOptions.some((j) => j.path === draft.javaPath) && <option value={draft.javaPath}>{draft.javaPath}</option>}
              </select>
            </label>

            <label className="field">
              <span className="field__label">{t('profiles.config')}</span>
              <select className="select" value={draft.configProfile ?? ''} onChange={(e) => patch({ configProfile: e.target.value || null })}>
                <option value="">{t('profiles.configLast')}</option>
                {configs.map((name) => <option key={name} value={name}>{name}</option>)}
                {draft.configProfile && !configs.includes(draft.configProfile) && <option value={draft.configProfile}>{draft.configProfile}</option>}
              </select>
            </label>

            <div className="field">
              <span className="field__label">{t('profiles.resolution')}</span>
              <div className="row">
                <select className="select resolution-mode" value={draft.resolution ? 'custom' : 'default'}
                  onChange={(e) => patch({ resolution: e.target.value === 'custom' ? { width: 1600, height: 900 } : null })}>
                  <option value="default">{t('profiles.resolutionDefault')}</option>
                  <option value="custom">W × H</option>
                </select>
                {draft.resolution && (
                  <>
                    <input className="input resolution-input" type="number" min={320} value={draft.resolution.width}
                      onChange={(e) => patch({ resolution: { ...draft.resolution!, width: Number(e.target.value) } })} />
                    <span className="muted">×</span>
                    <input className="input resolution-input" type="number" min={240} value={draft.resolution.height}
                      onChange={(e) => patch({ resolution: { ...draft.resolution!, height: Number(e.target.value) } })} />
                  </>
                )}
              </div>
            </div>

            <label className="field field--wide">
              <span className="field__label">{t('profiles.jvmArgs')}</span>
              <input className="input mono" placeholder="-XX:+UseStringDeduplication" value={draft.jvmArgs}
                onChange={(e) => patch({ jvmArgs: e.target.value })} />
            </label>
          </div>

          {error && <div className="alert alert--error">{error}</div>}

          <div className="profile-editor__actions">
            <button className="btn btn--ghost" onClick={() => void invoke('shell:open', 'instance', draft.targetId)}>
              <Icon name="folder" size={15} />
              {t('profiles.openFolder')}
            </button>
            <div className="row">
              <button className="btn btn--ghost" onClick={() => void create({ ...draft, name: `${draft.name} (2)` })}>
                <Icon name="copy" size={15} />
                {t('profiles.duplicate')}
              </button>
              <button className="btn btn--danger" disabled={profiles.length <= 1} onClick={() => void remove()}>
                <Icon name="trash" size={15} />
                {confirmDelete ? t('profiles.confirmDelete') : t('profiles.delete')}
              </button>
              <button className="btn btn--primary" disabled={!dirty} onClick={() => void save()}>
                {saved && !dirty ? <Icon name="check" size={15} /> : null}
                {saved && !dirty ? t('profiles.saved') : t('profiles.save')}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
