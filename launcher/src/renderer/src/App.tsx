import { useEffect } from 'react';
import { AccountDialog } from './components/AccountDialog';
import { Rail } from './components/Rail';
import { SetupWizard } from './components/SetupWizard';
import { TitleBar } from './components/TitleBar';
import { Changelog } from './pages/Changelog';
import { Home } from './pages/Home';
import { Profiles } from './pages/Profiles';
import { Settings } from './pages/Settings';
import { useStore, type Page } from './store';

/** Resolves the theme: AUTO uses Halloween during October (same rule as the client). */
/** AUTO: Halloween in October, Christmas from December to 6 January (same rule as the client's Theme). */
function resolveTheme(mode: string | undefined): 'default' | 'halloween' | 'christmas' {
  if (mode === 'halloween' || mode === 'default' || mode === 'christmas') {
    return mode;
  }
  const now = new Date();
  if (now.getMonth() === 9) {
    return 'halloween';
  }
  return now.getMonth() === 11 || (now.getMonth() === 0 && now.getDate() <= 6) ? 'christmas' : 'default';
}

export function App() {
  const ready = useStore((s) => s.ready);
  const page = useStore((s) => s.page);
  const settings = useStore((s) => s.settings);
  const accountDialog = useStore((s) => s.accountDialog);
  const init = useStore((s) => s.init);

  useEffect(() => {
    void init();
    if (import.meta.env.DEV) {
      // development automation hooks (see src/main/devAutomation.ts)
      (window as unknown as { __medirianDev: unknown }).__medirianDev = {
        navigate: (page: Page) => useStore.getState().navigate(page),
        completeSetup: () => useStore.getState().updateSettings({ setupCompleted: true }),
        reload: () => useStore.getState().init()
      };
    }
  }, [init]);

  useEffect(() => {
    document.documentElement.dataset.theme = resolveTheme(settings?.theme);
    document.documentElement.lang = settings?.language ?? 'en';
  }, [settings?.theme, settings?.language]);

  if (!ready || !settings) {
    return <div className="boot" />;
  }

  return (
    <div className="app">
      <Rail />
      <main className="main">
        <TitleBar />
        {page === 'home' && <Home />}
        {page === 'profiles' && <Profiles />}
        {page === 'changelog' && <Changelog />}
        {page === 'settings' && <Settings />}
      </main>
      {!settings.setupCompleted && <SetupWizard />}
      {accountDialog && <AccountDialog />}
    </div>
  );
}
