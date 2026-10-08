// Medirian Client download page: links to the newest GitHub release and the page in EN / PL.
(() => {
  const repo = document.documentElement.dataset.repo;
  const configured = /^[\w.-]+\/[\w.-]+$/.test(repo) && repo !== 'OWNER/REPO';
  const releases = `https://github.com/${repo}/releases`;
  // a stable link: GitHub redirects it to the installer of the newest release
  const installer = `${releases}/latest/download/MedirianClientSetup.exe`;

  const TEXT = {
    en: {
      'nav.features': 'Features', 'nav.download': 'Download', 'nav.releases': 'Releases',
      'hero.title': 'A cozy night in Minecraft',
      'hero.lead': 'Medirian Client is a launcher and a client for Minecraft 1.8.9, 1.21.8, 1.21.11 and 26.3 — 41 modules, a HUD in soft Liquid Glass, your own skin everywhere, cosmetics and mods from Modrinth in separate profiles.',
      'download.windows': 'Download for Windows', 'download.other': 'Also for', 'download.all': 'all releases',
      'download.version': 'Version {version} · {size} · Windows 10 and 11',
      'download.unconfigured': 'This page is not connected to a GitHub repository yet (data-repo in index.html).',
      'steps.title': 'Three steps to the night',
      'steps.1': 'Download <b>MedirianClientSetup.exe</b>.',
      'steps.2': 'Run it — Medirian Client installs for your Windows user, no administrator needed, with shortcuts on the desktop and in the Start Menu.',
      'steps.3': 'Open Medirian Client, sign in with Microsoft and press Play. Updates install themselves.',
      'features.title': 'What is inside',
      'f.mods.t': 'Mods per profile', 'f.mods.d': 'Search Modrinth, install with dependencies into one profile. Medirian checks the Minecraft version and loader for you.',
      'f.profiles.t': 'Separate profiles', 'f.profiles.d': 'Every profile has its own folder: mods, settings and worlds never mix. Share a profile with a friend as a short code.',
      'f.hud.t': '41 modules and a HUD', 'f.hud.d': 'Keystrokes, CPS, armor, coordinates, zoom, freelook and more on translucent glass — drag and scale every widget.',
      'f.cosmetics.t': 'Your skin, your style', 'f.cosmetics.d': 'Your Minecraft skin in the launcher and in the game menu. Capes, hats, wings, trails and emotes.',
      'f.perf.t': 'Fast', 'f.perf.d': 'Entity and block-entity culling, render distances per kind, fog and graphics options in one place, Java installed for you.',
      'f.updates.t': 'Always up to date', 'f.updates.d': 'The launcher and the client update in the background; your profiles and worlds stay where they are.',
      'foot.legal': 'Medirian Client is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.'
    },
    pl: {
      'nav.features': 'Funkcje', 'nav.download': 'Pobierz', 'nav.releases': 'Wydania',
      'hero.title': 'Przytulna noc w Minecrafcie',
      'hero.lead': 'Medirian Client to launcher i klient Minecrafta 1.8.9, 1.21.8, 1.21.11 i 26.3 — 41 modułów, HUD w delikatnym stylu Liquid Glass, Twój skin wszędzie, kosmetyki oraz mody z Modrinth w osobnych profilach.',
      'download.windows': 'Pobierz dla Windows', 'download.other': 'Także dla', 'download.all': 'wszystkie wydania',
      'download.version': 'Wersja {version} · {size} · Windows 10 i 11',
      'download.unconfigured': 'Ta strona nie jest jeszcze połączona z repozytorium GitHub (data-repo w index.html).',
      'steps.title': 'Trzy kroki do nocy',
      'steps.1': 'Pobierz <b>MedirianClientSetup.exe</b>.',
      'steps.2': 'Uruchom go — Medirian Client zainstaluje się dla Twojego użytkownika Windows, bez uprawnień administratora, ze skrótami na pulpicie i w menu Start.',
      'steps.3': 'Otwórz Medirian Client, zaloguj się kontem Microsoft i kliknij Graj. Aktualizacje instalują się same.',
      'features.title': 'Co jest w środku',
      'f.mods.t': 'Mody w profilach', 'f.mods.d': 'Szukaj w Modrinth, instaluj z zależnościami do jednego profilu. Medirian sam sprawdza wersję Minecrafta i loader.',
      'f.profiles.t': 'Osobne profile', 'f.profiles.d': 'Każdy profil ma własny folder: mody, ustawienia i światy się nie mieszają. Profil udostępnisz znajomemu jako krótki kod.',
      'f.hud.t': '41 modułów i HUD', 'f.hud.d': 'Keystrokes, CPS, zbroja, koordynaty, zoom, freelook i więcej na półprzezroczystym szkle — każdy widżet przesuniesz i przeskalujesz.',
      'f.cosmetics.t': 'Twój skin, Twój styl', 'f.cosmetics.d': 'Twój skin Minecrafta w launcherze i w menu gry. Peleryny, czapki, skrzydła, ślady i emotki.',
      'f.perf.t': 'Szybki', 'f.perf.d': 'Culling bytów i bloków, osobne dystanse rysowania, mgła i opcje grafiki w jednym miejscu, Java instalowana automatycznie.',
      'f.updates.t': 'Zawsze aktualny', 'f.updates.d': 'Launcher i klient aktualizują się w tle; profile i światy zostają na swoim miejscu.',
      'foot.legal': 'Medirian Client nie jest oficjalnym produktem Minecraft i nie jest zatwierdzony przez Mojang ani Microsoft ani z nimi powiązany.'
    }
  };

  const stored = (() => { try { return localStorage.getItem('medirian-lang'); } catch { return null; } })();
  let lang = stored === 'pl' || stored === 'en' ? stored : (navigator.language || 'en').toLowerCase().startsWith('pl') ? 'pl' : 'en';
  let release = null;

  const t = (key, vars = {}) => (TEXT[lang][key] ?? TEXT.en[key] ?? key).replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? '');
  const mb = (bytes) => `${(bytes / 1024 / 1024).toFixed(0)} MB`;

  function render() {
    document.documentElement.lang = lang;
    document.querySelectorAll('[data-t]').forEach((el) => { el.innerHTML = t(el.dataset.t); });
    document.querySelector('.lang').textContent = lang === 'pl' ? 'EN' : 'PL';
    const meta = document.querySelector('.js-meta');
    const exe = release?.assets.find((a) => a.name === 'MedirianClientSetup.exe');
    meta.textContent = exe ? t('download.version', { version: release.tag_name.replace(/^v/, ''), size: mb(exe.size) }) : 'MedirianClientSetup.exe';
    const note = document.querySelector('.js-note');
    note.hidden = configured;
    note.textContent = configured ? '' : t('download.unconfigured');
  }

  function links() {
    const set = (selector, href) => document.querySelectorAll(selector).forEach((a) => { a.href = href; });
    if (!configured) {
      set('.js-windows, .js-mac, .js-linux, .js-releases', '#download');
      return;
    }
    set('.js-windows', installer);
    set('.js-releases', releases);
    const asset = (test) => release?.assets.find((a) => test(a.name))?.browser_download_url;
    // macOS: the build for this Mac's chip when the browser tells, else the first disk image
    const arm = /arm|aarch64/i.test(navigator.userAgentData?.platform ?? '') || /Mac.*(ARM|Apple)/.test(navigator.userAgent);
    set('.js-mac', asset((n) => n.endsWith(`${arm ? 'arm64' : 'x64'}.dmg`)) ?? asset((n) => n.endsWith('.dmg')) ?? releases + '/latest');
    set('.js-linux', asset((n) => n.endsWith('.AppImage')) ?? releases + '/latest');
  }

  document.querySelector('.lang').addEventListener('click', () => {
    lang = lang === 'pl' ? 'en' : 'pl';
    try { localStorage.setItem('medirian-lang', lang); } catch { /* private mode */ }
    render();
  });

  render();
  links();
  if (configured) {
    // version, size and the macOS / Linux files of the newest release (the Windows link works without it)
    fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: 'application/vnd.github+json' } })
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (data?.assets) {
          release = data;
          render();
          links();
        }
      })
      .catch(() => { /* offline or rate limited: the stable links stay */ });
  }
})();
