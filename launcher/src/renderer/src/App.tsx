import { useEffect } from 'react';
import { AccountDialog } from './components/AccountDialog';
import { Scene } from './components/Scene';
import { SetupWizard } from './components/SetupWizard';
import { TopBar } from './components/TopBar';
import { Changelog } from './pages/Changelog';
import { Home } from './pages/Home';
import { Profiles } from './pages/Profiles';
import { Settings } from './pages/Settings';
import { useStore, type Page } from './store';

/** December to 6 January (same rule as the client's Theme). */
function isWinter(date: Date): boolean {
  return date.getMonth() === 11 || (date.getMonth() === 0 && date.getDate() <= 6);
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
        reload: () => useStore.getState().init(),
        account: (open: boolean) => useStore.getState().setAccountDialog(open)
      };
    }
  }, [init]);

  useEffect(() => {
    const root = document.documentElement;
    root.lang = settings?.language ?? 'en';
    root.dataset.motion = settings?.sceneMotion === false ? 'off' : 'on';
    root.dataset.snow = settings?.winterSnow !== false && isWinter(new Date()) ? 'on' : 'off';
  }, [settings?.language, settings?.sceneMotion, settings?.winterSnow]);

  if (!ready || !settings) {
    return <div className="boot" />;
  }

  return (
    <div className="app">
      <Scene dim={page !== 'home'} home={page === 'home'} motion={settings.sceneMotion} />
      <TopBar />
      <main className="stage">
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
