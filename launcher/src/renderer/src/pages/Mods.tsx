import { useCallback, useEffect, useMemo, useState } from 'react';
import type {
  InstalledMod,
  InstalledModsState,
  ModCategory,
  ModDetails,
  ModInstallPlan,
  ModIssue,
  ModSearchResult,
  ModSort,
  ModSummary,
  ModTargetInfo
} from '../../../common/types';
import { errorMessage, invoke } from '../api';
import { ArtIcon, Icon } from '../components/Icon';
import { useT, type MessageKey } from '../i18n';
import { selectedProfile, useStore } from '../store';

const PAGE_SIZE = 20;

function compact(n: number): string {
  if (n >= 1_000_000) {
    return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}M`;
  }
  if (n >= 1000) {
    return `${(n / 1000).toFixed(n >= 10_000 ? 0 : 1)}k`;
  }
  return String(n);
}

function size(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/** An install problem or note in the user's language. */
function issueText(t: ReturnType<typeof useT>, issue: ModIssue, target: ModTargetInfo): string {
  const { code, ...names } = issue;
  const { version: modVersion, ...rest } = names as Record<string, string>;
  return t(`mods.issue.${code}` as MessageKey, { ...rest, modVersion: modVersion ?? '', version: target.minecraftVersion, loader: target.loaderName });
}

/** A mod's icon, or a pixel placeholder when it has none or it fails to load. */
function ModIcon({ url, size: px = 48 }: { url: string | null; size?: number }) {
  const [failed, setFailed] = useState(false);
  return (
    <span className="mod-icon px-inset" style={{ width: px + 8, height: px + 8 }}>
      {url && !failed
        ? <img src={url} width={px} height={px} alt="" draggable={false} loading="lazy" onError={() => setFailed(true)} />
        : <ArtIcon name="cat-misc" scale={px >= 32 ? 2 : 1} />}
    </span>
  );
}

/**
 * Mods: browse Modrinth for the chosen profile's Minecraft version and loader,
 * install with dependencies, and manage the profile's installed mods (enable, disable, update,
 * remove). Every action works on that profile's own mods folder.
 */
export function Mods() {
  const t = useT();
  const profiles = useStore((s) => s.profiles);
  const state = useStore();
  const game = useStore((s) => s.game);
  const task = useStore((s) => s.modTask);
  const [profileId, setProfileId] = useState(() => selectedProfile(state)?.id ?? profiles[0]?.id ?? '');
  const [tab, setTab] = useState<'browse' | 'installed'>('browse');
  const [installed, setInstalled] = useState<InstalledModsState | null>(null);
  const [details, setDetails] = useState<string | null>(null);
  const [plan, setPlan] = useState<{ plan: ModInstallPlan; name: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const profile = profiles.find((p) => p.id === profileId) ?? profiles[0];
  const running = (game.state === 'running' || game.state === 'preparing') && game.profileId === profile?.id;

  const refreshInstalled = useCallback(async () => {
    if (!profile) {
      return;
    }
    try {
      setInstalled(await invoke('mods:installed', profile.id));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [profile?.id]);

  useEffect(() => {
    setInstalled(null);
    void refreshInstalled();
  }, [refreshInstalled]);

  /** Shows what an install does; installs right away when it is just the one file. */
  const requestInstall = async (projectId: string, name: string, versionId?: string) => {
    setError(null);
    setBusy(projectId);
    try {
      const next = await invoke('mods:plan', profile.id, projectId, versionId);
      if (next.problems.length === 0 && next.steps.length === 1 && next.warnings.length === 0) {
        setInstalled(await invoke('mods:install', profile.id, projectId, versionId));
      } else {
        setPlan({ plan: next, name });
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const confirmPlan = async () => {
    if (!plan) {
      return;
    }
    const first = plan.plan.steps.find((s) => s.reason === 'requested') ?? plan.plan.steps[0];
    setBusy(first.projectId);
    setError(null);
    try {
      setInstalled(await invoke('mods:install', plan.plan.profileId, first.projectId, first.versionId));
      setPlan(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  if (!profile) {
    return null;
  }
  const target = installed?.target;

  return (
    <div className="page">
      <div className="window px-frame">
        <div className="window__header">
          <ArtIcon name="mods" scale={2} />
          <div>
            <h1 className="window__title">{t('mods.title')}</h1>
            <p className="window__subtitle">{t('mods.subtitle')}</p>
          </div>
          <div className="window__actions mods-profile">
            <label className="mods-profile__label pixel" htmlFor="mods-profile">{t('mods.profile')}</label>
            <select id="mods-profile" className="select" value={profile.id} onChange={(e) => {
              setProfileId(e.target.value);
              setDetails(null);
            }}>
              {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            {target && <span className="chip chip--muted">{`Minecraft ${target.minecraftVersion} · ${target.loaderName}`}</span>}
          </div>
        </div>

        <div className="mods-tabs">
          <button className={`mods-tab pixel${tab === 'browse' ? ' mods-tab--active' : ''}`} onClick={() => setTab('browse')}>
            <Icon name="search" size={16} />
            {t('mods.browse')}
          </button>
          <button className={`mods-tab pixel${tab === 'installed' ? ' mods-tab--active' : ''}`} onClick={() => setTab('installed')}>
            <Icon name="box" size={16} />
            {t('mods.installed')}
            {installed && <span className="chip chip--muted mods-tab__count">{installed.mods.length}</span>}
          </button>
          {task && task.profileId === profile.id && (
            <div className="mods-task">
              <span className="spinner" />
              <span>{task.label}</span>
              <div className="xpbar px-inset mods-task__bar">
                <div className="xpbar__fill" style={{ width: `${task.total ? Math.round((task.done / task.total) * 100) : 0}%` }} />
              </div>
            </div>
          )}
        </div>

        {running && <div className="alert alert--warn mods-alert">{t('mods.running')}</div>}
        {error && (
          <div className="alert alert--error mods-alert">
            <Icon name="alert" size={16} />
            <span>{error}</span>
            <button className="btn btn--ghost btn--small btn--icon" onClick={() => setError(null)} aria-label={t('account.close')}>
              <Icon name="x" size={16} />
            </button>
          </div>
        )}

        {tab === 'browse'
          ? <Browse profileId={profile.id} installed={installed} busy={busy} locked={running}
              onInstall={requestInstall} onOpen={setDetails}
              onUpdate={async (mod) => {
                setBusy(mod.projectId);
                try {
                  setInstalled(await invoke('mods:update', profile.id, mod.file));
                } catch (e) {
                  setError(errorMessage(e));
                } finally {
                  setBusy(null);
                }
              }} />
          : <Installed state={installed} profileId={profile.id} locked={running} onChange={setInstalled} onError={setError}
              onOpen={setDetails} />}
      </div>

      {details && (
        <DetailsDialog projectId={details} profileId={profile.id} installed={installed}
          locked={running} busy={busy} onClose={() => setDetails(null)}
          onInstall={(name, versionId) => void requestInstall(details, name, versionId)} />
      )}
      {plan && <PlanDialog plan={plan.plan} name={plan.name} profileName={profile.name} busy={busy !== null}
        onCancel={() => setPlan(null)} onConfirm={() => void confirmPlan()} />}
    </div>
  );
}

// ------------------------------------------------------------------ browse

function Browse({ profileId, installed, busy, locked, onInstall, onOpen, onUpdate }: {
  profileId: string;
  installed: InstalledModsState | null;
  busy: string | null;
  locked: boolean;
  onInstall: (projectId: string, name: string) => void;
  onOpen: (projectId: string) => void;
  onUpdate: (mod: InstalledMod) => void;
}) {
  const t = useT();
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [categories, setCategories] = useState<ModCategory[]>([]);
  const [category, setCategory] = useState<string | null>(null);
  const [sort, setSort] = useState<ModSort>('relevance');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<ModSearchResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query.trim()), 350);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    void invoke('mods:categories').then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => setPage(0), [debounced, category, sort, profileId]);

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(null);
    invoke('mods:search', { query: debounced, profileId, category, sort, page, pageSize: PAGE_SIZE })
      .then((next) => current && setResult(next))
      .catch((e) => current && setError(errorMessage(e)))
      .finally(() => current && setLoading(false));
    return () => {
      current = false;
    };
  }, [debounced, category, sort, page, profileId, attempt]);

  const byProject = useMemo(() => {
    const map = new Map<string, InstalledMod>();
    for (const mod of installed?.mods ?? []) {
      if (mod.source === 'modrinth' && mod.projectId) {
        map.set(mod.projectId, mod);
      }
    }
    return map;
  }, [installed]);

  const pages = result ? Math.max(1, Math.ceil(result.total / PAGE_SIZE)) : 1;

  return (
    <div className="mods-browse">
      <div className="mods-toolbar">
        <label className="mods-search px-inset">
          <Icon name="search" size={16} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('mods.searchPlaceholder')} spellCheck={false} />
          {query && (
            <button className="mods-search__clear" onClick={() => setQuery('')} aria-label={t('mods.clear')}>
              <Icon name="x" size={16} />
            </button>
          )}
        </label>
        <span className="mods-source pixel" title="modrinth.com">Modrinth</span>
        <select className="select mods-filter" value={category ?? ''} onChange={(e) => setCategory(e.target.value || null)}>
          <option value="">{t('mods.allCategories')}</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="select mods-filter" value={sort} onChange={(e) => setSort(e.target.value as ModSort)}>
          <option value="relevance">{t('mods.sort.relevance')}</option>
          <option value="downloads">{t('mods.sort.downloads')}</option>
          <option value="updated">{t('mods.sort.updated')}</option>
          <option value="newest">{t('mods.sort.newest')}</option>
        </select>
      </div>
      <div className="mods-results">
        {error ? (
          <div className="mods-state">
            <ArtIcon name="cat-misc" scale={3} />
            <p className="pixel">{t('mods.error')}</p>
            <p className="dim">{error}</p>
            <button className="btn" onClick={() => setAttempt((n) => n + 1)}><Icon name="refresh" size={16} />{t('mods.retry')}</button>
          </div>
        ) : loading && !result ? (
          <div className="mods-grid">
            {Array.from({ length: 8 }, (_, i) => <div key={i} className="mod-card mod-card--skeleton px-frame px-frame--surface" />)}
          </div>
        ) : result && result.hits.length === 0 ? (
          <div className="mods-state">
            <ArtIcon name="cat-misc" scale={3} />
            <p className="pixel">{t('mods.empty')}</p>
            <p className="dim">{t('mods.emptyHint', { version: result.target.minecraftVersion, loader: result.target.loaderName })}</p>
          </div>
        ) : (
          <div className={`mods-grid${loading ? ' mods-grid--loading' : ''}`}>
            {result?.hits.map((mod) => (
              <ModCard key={mod.projectId} mod={mod} target={result.target} installed={byProject.get(mod.projectId)}
                busy={busy === mod.projectId} locked={locked}
                onInstall={() => onInstall(mod.projectId, mod.name)} onOpen={() => onOpen(mod.projectId)}
                onUpdate={onUpdate} />
            ))}
          </div>
        )}
      </div>

      {result && result.total > PAGE_SIZE && (
        <div className="pager">
          <button className="btn btn--small" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>
            <Icon name="back" size={16} />{t('mods.previous')}
          </button>
          <span className="pager__label pixel">{t('mods.page', { page: page + 1, pages })}</span>
          <button className="btn btn--small" disabled={page + 1 >= pages || loading} onClick={() => setPage(page + 1)}>
            {t('mods.next')}<Icon name="chevronRight" size={16} />
          </button>
        </div>
      )}
    </div>
  );
}

function ModCard({ mod, target, installed, busy, locked, onInstall, onOpen, onUpdate }: {
  mod: ModSummary;
  target: ModSearchResult['target'];
  installed: InstalledMod | undefined;
  busy: boolean;
  locked: boolean;
  onInstall: () => void;
  onOpen: () => void;
  onUpdate: (mod: InstalledMod) => void;
}) {
  const t = useT();
  return (
    <article className="mod-card px-frame px-frame--surface">
      <button className="mod-card__main" onClick={onOpen} title={t('mods.details')}>
        <ModIcon url={mod.iconUrl} />
        <span className="mod-card__text">
          <span className="mod-card__name pixel">{mod.name}</span>
          <span className="mod-card__author">
            {mod.author && <span className="mod-card__by">{t('mods.by', { author: mod.author })}</span>}
            <span className="mod-card__downloads" title={t('mods.downloads')}><Icon name="download" size={16} />{compact(mod.downloads)}</span>
          </span>
          <span className="mod-card__desc">{mod.description}</span>
        </span>
      </button>
      <div className="mod-card__foot">
        <span className="mod-card__tags">
          {mod.categories[0] && <span className="tag">{mod.categories[0]}</span>}
          <span className="tag tag--version">{target.minecraftVersion}</span>
          <span className="tag tag--loader">{target.loaderName}</span>
        </span>
        {installed?.update ? (
          <button className="btn btn--primary btn--small" disabled={busy || locked} onClick={() => onUpdate(installed)}>
            {busy ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
            {t('mods.update')}
          </button>
        ) : installed ? (
          <span className="mod-card__installed pixel"><Icon name="check" size={16} />{t('mods.installedLabel')}</span>
        ) : (
          <button className="btn btn--primary btn--small" disabled={busy || locked} onClick={onInstall}>
            {busy ? <span className="spinner" /> : <Icon name="download" size={16} />}
            {t('mods.install')}
          </button>
        )}
      </div>
    </article>
  );
}

// ------------------------------------------------------------------ installed

function Installed({ state, profileId, locked, onChange, onError, onOpen }: {
  state: InstalledModsState | null;
  profileId: string;
  locked: boolean;
  onChange: (state: InstalledModsState) => void;
  onError: (message: string) => void;
  onOpen: (projectId: string) => void;
}) {
  const t = useT();
  const [working, setWorking] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<string | null>(null);
  const run = async (key: string, action: () => Promise<InstalledModsState>) => {
    setWorking(key);
    try {
      onChange(await action());
    } catch (e) {
      onError(errorMessage(e));
    } finally {
      setWorking(null);
    }
  };

  if (!state) {
    return <div className="mods-state"><span className="spinner" /></div>;
  }
  const updates = state.mods.filter((m) => m.update).length;
  return (
    <div className="mods-installed">
      <div className="mods-toolbar">
        <span className="mods-installed__count dim">{t('mods.installedCount', { count: state.mods.length })}</span>
        <span className="mods-installed__checked muted">
          {state.checkedAt ? t('mods.checked', { time: new Date(state.checkedAt).toLocaleTimeString() }) : ''}
        </span>
        <div className="row">
          <button className="btn btn--small" disabled={working !== null}
            onClick={() => void run('check', () => invoke('mods:checkUpdates', profileId))}>
            {working === 'check' ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
            {t('mods.checkUpdates')}
          </button>
          <button className="btn btn--small btn--ghost" onClick={() => void invoke('shell:open', 'mods', profileId)}>
            <Icon name="folder" size={16} />
            {t('mods.openFolder')}
          </button>
        </div>
      </div>
      {updates > 0 && <div className="alert mods-alert">{t('mods.updatesAvailable', { count: updates })}</div>}

      {state.mods.length === 0 ? (
        <div className="mods-state">
          <ArtIcon name="cat-misc" scale={3} />
          <p className="pixel">{t('mods.noneInstalled')}</p>
          <p className="dim">{t('mods.noneInstalledHint')}</p>
        </div>
      ) : (
        <div className="installed-list">
          {state.mods.map((mod) => {
            const missing = mod.dependencies.filter((d) => !d.installed);
            return (
              <div key={mod.file} className={`installed px-frame px-frame--surface${mod.enabled ? '' : ' installed--off'}`}>
                <ModIcon url={mod.iconUrl} size={32} />
                <div className="installed__text">
                  <div className="installed__title">
                    <span className="pixel installed__name">{mod.name}</span>
                    <span className="installed__version">{mod.versionNumber}</span>
                    <span className={`tag tag--${mod.source}`}>{mod.source === 'local' ? t('mods.local') : 'Modrinth'}</span>
                    {mod.update && <span className="tag tag--update">{t('mods.updateTo', { version: mod.update.versionNumber })}</span>}
                  </div>
                  <div className="installed__meta">
                    {mod.author && <span>{t('mods.by', { author: mod.author })}</span>}
                    <span className="muted">{mod.file}{mod.enabled ? '' : '.disabled'} · {size(mod.size)}</span>
                  </div>
                  {(mod.dependencies.length > 0 || mod.requiredBy.length > 0) && (
                    <div className="installed__deps">
                      {mod.dependencies.length > 0 && (
                        <span>
                          {t('mods.requires')}{' '}
                          {mod.dependencies.map((d, i) => (
                            <span key={d.name} className={d.installed ? 'dep dep--ok' : 'dep dep--missing'}>
                              {i > 0 ? ', ' : ''}{d.name}{d.installed ? '' : ` (${t('mods.missing')})`}
                            </span>
                          ))}
                        </span>
                      )}
                      {mod.requiredBy.length > 0 && <span className="muted">{t('mods.requiredBy', { names: mod.requiredBy.join(', ') })}</span>}
                    </div>
                  )}
                  {missing.length > 0 && mod.enabled && <div className="installed__warn">{t('mods.missingWarning')}</div>}
                </div>
                <div className="installed__actions">
                  {mod.update && (
                    <button className="btn btn--primary btn--small" disabled={locked || working !== null}
                      onClick={() => void run(mod.file, () => invoke('mods:update', profileId, mod.file))}>
                      {working === mod.file ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
                      {t('mods.update')}
                    </button>
                  )}
                  {mod.projectId && mod.source === 'modrinth' && (
                    <button className="btn btn--ghost btn--small btn--icon" title={t('mods.details')}
                      onClick={() => onOpen(mod.projectId!)}>
                      <Icon name="info" size={16} />
                    </button>
                  )}
                  {mod.pageUrl && /^https:\/\/modrinth\.com\//.test(mod.pageUrl) && (
                    <button className="btn btn--ghost btn--small btn--icon" title={t('mods.openPage')}
                      onClick={() => void invoke('shell:openExternal', mod.pageUrl!)}>
                      <Icon name="external" size={16} />
                    </button>
                  )}
                  <button className={`toggle${mod.enabled ? ' toggle--on' : ''}`} aria-pressed={mod.enabled}
                    title={mod.enabled ? t('mods.disable') : t('mods.enable')} disabled={locked || working !== null}
                    onClick={() => void run(mod.file + ':toggle', () => invoke('mods:setEnabled', profileId, mod.file, !mod.enabled))} />
                  <button className={`btn btn--small ${confirm === mod.file ? 'btn--danger' : 'btn--ghost btn--icon'}`} disabled={locked || working !== null}
                    title={t('mods.remove')}
                    onClick={() => {
                      if (confirm !== mod.file) {
                        setConfirm(mod.file);
                        return;
                      }
                      setConfirm(null);
                      void run(mod.file + ':remove', () => invoke('mods:remove', profileId, mod.file));
                    }}>
                    <Icon name="trash" size={16} />
                    {confirm === mod.file && (mod.requiredBy.length > 0 ? t('mods.removeRequired') : t('mods.removeConfirm'))}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ dialogs

function DetailsDialog({ projectId, profileId, installed, locked, busy, onClose, onInstall }: {
  projectId: string;
  profileId: string;
  installed: InstalledModsState | null;
  locked: boolean;
  busy: string | null;
  onClose: () => void;
  onInstall: (name: string, versionId?: string) => void;
}) {
  const t = useT();
  const [details, setDetails] = useState<ModDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setDetails(null);
    invoke('mods:details', projectId, profileId).then(setDetails).catch((e) => setError(errorMessage(e)));
  }, [projectId, profileId]);
  const current = installed?.mods.find((m) => m.source === 'modrinth' && m.projectId === projectId);
  const compatible = details?.versions.filter((v) => v.compatible) ?? [];

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="dialog dialog--wide px-frame mod-details">
        <div className="dialog__header">
          <Icon name="box" size={16} />
          <h2 className="dialog__title">{details?.name ?? t('mods.details')}</h2>
          <button className="btn btn--ghost btn--small btn--icon" aria-label={t('account.close')} onClick={onClose}><Icon name="close" size={16} /></button>
        </div>
        <div className="dialog__body">
          {error && <div className="alert alert--error">{error}</div>}
          {!details && !error && <div className="mods-state"><span className="spinner" /></div>}
          {details && (
            <>
              <div className="mod-details__head">
                <ModIcon url={details.iconUrl} size={64} />
                <div className="mod-details__info">
                  <div className="pixel mod-details__name">{details.name}</div>
                  {details.author && <div className="dim">{t('mods.by', { author: details.author })}</div>}
                  <div className="mod-details__tags">
                    {details.categories.slice(0, 4).map((c) => <span key={c} className="tag">{c}</span>)}
                    {details.loaders.slice(0, 4).map((l) => <span key={l} className="tag tag--loader">{l}</span>)}
                    <span className="tag"><Icon name="download" size={16} />{compact(details.downloads)}</span>
                  </div>
                </div>
              </div>
              <p className="mod-details__desc">{details.description}</p>
              <div className="mod-details__facts">
                <div><span className="field__label">{t('mods.source')}</span><span>Modrinth</span></div>
                <div><span className="field__label">{t('mods.versions')}</span><span>{details.gameVersions.slice(0, 8).join(', ')}{details.gameVersions.length > 8 ? '…' : ''}</span></div>
                <div><span className="field__label">{t('mods.forProfile')}</span><span>{`Minecraft ${details.target.minecraftVersion} · ${details.target.loaderName}`}</span></div>
              </div>
              {compatible.length === 0 ? (
                <div className="alert alert--warn">{t('mods.incompatible', { version: details.target.minecraftVersion, loader: details.target.loaderName })}</div>
              ) : (
                <div className="versions">
                  <div className="field__label">{t('mods.compatibleVersions')}</div>
                  {compatible.slice(0, 8).map((v, i) => (
                    <div key={v.id} className="version-row">
                      <span className="pixel">{v.versionNumber}</span>
                      {v.releaseType !== 'release' && <span className={`tag tag--${v.releaseType}`}>{v.releaseType}</span>}
                      {i === 0 && <span className="tag tag--update">{t('mods.newest')}</span>}
                      <span className="muted">{new Date(v.publishedAt).toLocaleDateString()} · {size(v.size)}</span>
                      {current?.versionId === v.id ? (
                        <span className="mod-card__installed pixel"><Icon name="check" size={16} />{t('mods.installedLabel')}</span>
                      ) : (
                        <button className="btn btn--small" disabled={locked || busy !== null} onClick={() => onInstall(details.name, v.id)}>
                          <Icon name="download" size={16} />{current ? t('mods.switchVersion') : t('mods.install')}
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
        <div className="dialog__actions">
          {details && (
            <button className="btn btn--ghost" onClick={() => void invoke('shell:openExternal', details.pageUrl)}>
              <Icon name="external" size={16} />{t('mods.openPage')}
            </button>
          )}
          {details && compatible.length > 0 && !current && (
            <button className="btn btn--primary" disabled={locked || busy !== null} onClick={() => onInstall(details.name)}>
              {busy ? <span className="spinner" /> : <Icon name="download" size={16} />}{t('mods.installNewest')}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function PlanDialog({ plan, name, profileName, busy, onCancel, onConfirm }: {
  plan: ModInstallPlan;
  name: string;
  profileName: string;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const t = useT();
  const blocked = plan.problems.length > 0;
  const total = plan.steps.reduce((sum, s) => sum + s.size, 0);
  return (
    <div className="overlay">
      <div className="dialog px-frame plan">
        <div className="dialog__header">
          <Icon name={blocked ? 'alert' : 'download'} size={16} />
          <h2 className="dialog__title">{blocked ? t('mods.cannotInstall', { name }) : t('mods.installInto', { name, profile: profileName })}</h2>
        </div>
        <div className="dialog__body">
          {plan.problems.map((p, i) => <div key={i} className="alert alert--error">{issueText(t, p, plan.target)}</div>)}
          {!blocked && (
            <>
              <p className="dim">{plan.steps.length > 1 ? t('mods.planWithDeps', { count: plan.steps.length - 1 }) : t('mods.planSingle')}</p>
              <ul className="plan__steps">
                {plan.steps.map((step) => (
                  <li key={step.projectId} className="plan__step">
                    <Icon name={step.reason === 'requested' ? 'box' : 'chevronRight'} size={16} />
                    <span className="pixel">{step.name}</span>
                    <span className="muted">{step.versionNumber}</span>
                    {step.reason === 'dependency' && <span className="tag">{t('mods.dependency')}</span>}
                    <span className="muted plan__size">{size(step.size)}</span>
                  </li>
                ))}
              </ul>
              {plan.satisfied.length > 0 && <p className="muted">{t('mods.alreadyThere', { names: plan.satisfied.join(', ') })}</p>}
              <p className="muted">{t('mods.planTarget', { version: plan.target.minecraftVersion, loader: plan.target.loaderName, size: size(total) })}</p>
            </>
          )}
          {plan.warnings.map((w, i) => <div key={i} className="alert alert--warn">{issueText(t, w, plan.target)}</div>)}
        </div>
        <div className="dialog__actions">
          <button className="btn" onClick={onCancel}>{blocked ? t('account.close') : t('mods.cancel')}</button>
          {!blocked && (
            <button className="btn btn--primary" disabled={busy} onClick={onConfirm}>
              {busy ? <span className="spinner" /> : <Icon name="download" size={16} />}
              {t('mods.installCount', { count: plan.steps.length })}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
