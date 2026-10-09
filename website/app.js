// Medirian Client website: the newest GitHub release behind the download buttons, the Medirian Services
// status, the navigation, the small demos (screenshots, HUD look, settings, capes) and English / Polish.
// Configuration comes from the JSON in index.html, which scripts/build-website.mjs fills in.
(() => {
  'use strict';

  const root = document.documentElement;
  root.classList.add('js');
  const $ = (selector, scope = document) => scope.querySelector(selector);
  const $$ = (selector, scope = document) => [...scope.querySelectorAll(selector)];
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ------------------------------------------------------------------ configuration

  const config = (() => {
    try {
      return JSON.parse($('#medirian-config').textContent);
    } catch {
      return {};
    }
  })();
  const https = (value) => (typeof value === 'string' && /^https:\/\/[^\s"'<>]+$/.test(value) ? value.replace(/\/+$/, '') : '');
  const repo = typeof config.repo === 'string' && /^[\w.-]+\/[\w.-]+$/.test(config.repo) ? config.repo : '';
  const servicesUrl = https(config.servicesUrl);
  const INSTALLER = 'MedirianClientSetup.exe';
  const releasesPage = repo ? `https://github.com/${repo}/releases` : '';
  // GitHub redirects this to the installer of the newest release
  const stableInstaller = repo ? `${releasesPage}/latest/download/${INSTALLER}` : '';

  // ------------------------------------------------------------------ language

  // Texts written by this script; the page's own texts are in index.html (English) and pl.js (Polish).
  const TEXT = {
    en: {
      soon: 'Coming soon',
      unconfigured: "This page isn't connected to a release channel yet, so there is nothing to download here.",
      none: "The first public release isn't out yet. The installer appears here as soon as it's published.",
      follow: 'Follow the releases on GitHub',
      noInstaller: "The newest release doesn't include the Windows installer. Its files are on the release page.",
      releasePage: 'Open the release page',
      also: 'Also for',
      all: 'all releases',
      online: 'Online',
      offline: 'Unreachable right now',
      checking: 'Checking…'
    },
    pl: {
      soon: 'Już wkrótce',
      unconfigured: 'Ta strona nie jest jeszcze połączona z kanałem wydań, więc nie ma tu nic do pobrania.',
      none: 'Pierwsze publiczne wydanie jeszcze się nie ukazało. Instalator pojawi się tutaj zaraz po publikacji.',
      follow: 'Śledź wydania na GitHubie',
      noInstaller: 'Najnowsze wydanie nie zawiera instalatora dla Windows. Jego pliki są na stronie wydania.',
      releasePage: 'Otwórz stronę wydania',
      also: 'Także dla',
      all: 'wszystkie wydania',
      online: 'Działa',
      offline: 'Chwilowo niedostępne',
      checking: 'Sprawdzanie…'
    }
  };

  const stored = (() => {
    try {
      return localStorage.getItem('medirian-lang');
    } catch {
      return null;
    }
  })();
  let lang = stored === 'pl' || stored === 'en' ? stored : (navigator.language || '').toLowerCase().startsWith('pl') ? 'pl' : 'en';
  const t = (key) => TEXT[lang][key] ?? TEXT.en[key];

  const original = new Map();
  const originalLabels = new Map();
  const originalTitle = document.title;

  function loadPolish() {
    if (window.MEDIRIAN_PL) return Promise.resolve();
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'pl.js';
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('pl.js did not load'));
      document.head.append(script);
    });
  }

  function applyLanguage() {
    const pl = lang === 'pl' ? window.MEDIRIAN_PL ?? {} : {};
    root.lang = lang;
    $$('[data-t]').forEach((el) => {
      if (!original.has(el)) original.set(el, el.innerHTML);
      el.innerHTML = pl[el.dataset.t] ?? original.get(el);
    });
    $$('[data-t-label]').forEach((el) => {
      if (!originalLabels.has(el)) originalLabels.set(el, el.getAttribute('aria-label'));
      el.setAttribute('aria-label', pl[el.dataset.tLabel] ?? originalLabels.get(el));
    });
    document.title = pl['meta.title'] ?? originalTitle;
    const button = $('.js-lang');
    button.textContent = lang === 'pl' ? 'EN' : 'PL';
    button.lang = lang === 'pl' ? 'en' : 'pl';
    button.setAttribute('aria-label', lang === 'pl' ? 'English' : 'Polski');
    renderDownload();
    renderStatus();
  }

  function setLanguage(next) {
    lang = next;
    try {
      localStorage.setItem('medirian-lang', lang);
    } catch {
      /* private mode */
    }
    (lang === 'pl' ? loadPolish() : Promise.resolve()).then(applyLanguage, () => {
      lang = 'en';
      applyLanguage();
    });
  }

  $('.js-lang').addEventListener('click', () => setLanguage(lang === 'pl' ? 'en' : 'pl'));

  // ------------------------------------------------------------------ download

  // state: 'unconfigured' | 'loading' | 'ready' | 'none' | 'noInstaller' | 'unknown'
  let download = { state: repo ? 'loading' : 'unconfigured', release: null };

  const megabytes = (bytes) => `${Math.round(bytes / 1024 / 1024)} MB`;
  const asset = (test) => download.release?.assets.find((a) => test(a.name));

  function link(href, text) {
    const a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    a.rel = 'noopener';
    return a;
  }

  function renderDownload() {
    const { state, release } = download;
    const exe = asset((name) => name === INSTALLER);
    const available = state === 'ready' || state === 'loading' || state === 'unknown';
    const href = state === 'ready' ? exe.browser_download_url : state === 'noInstaller' ? release.html_url : stableInstaller;

    $$('.js-installer').forEach((button) => {
      const inDownload = button.closest('#download');
      if (available || state === 'noInstaller') {
        button.href = href;
        button.removeAttribute('aria-disabled');
      } else if (inDownload) {
        button.removeAttribute('href');
        button.setAttribute('aria-disabled', 'true');
      } else {
        button.href = '#download'; // the hero button leads to the explanation
      }
    });
    const label = $('.js-installer-label');
    if (available || state === 'noInstaller') {
      // the page text (English or Polish) of the button
      label.innerHTML = (lang === 'pl' ? window.MEDIRIAN_PL?.['dl.button'] : null) ?? original.get(label) ?? label.innerHTML;
    } else {
      if (!original.has(label)) original.set(label, label.innerHTML);
      label.textContent = t('soon');
    }

    const version = release ? release.tag_name.replace(/^v/i, '') : '';
    $$('.js-version').forEach((el) => {
      el.textContent = version;
    });
    $('.js-version-line').hidden = state !== 'ready';
    $('.js-version-item').hidden = state !== 'ready';
    $('.js-size-item').hidden = state !== 'ready';
    $('.js-date-item').hidden = state !== 'ready' || !release.published_at;
    if (state === 'ready') {
      $('.js-size').textContent = megabytes(exe.size);
      if (release.published_at) {
        $('.js-date').textContent = new Date(release.published_at).toLocaleDateString(lang === 'pl' ? 'pl-PL' : 'en-GB', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        });
      }
    }

    const note = $('.js-state');
    note.replaceChildren();
    note.hidden = !['unconfigured', 'none', 'noInstaller'].includes(state);
    if (state === 'unconfigured') note.append(t('unconfigured'));
    if (state === 'none') note.append(t('none'), ' ', link(releasesPage, t('follow')));
    if (state === 'noInstaller') note.append(t('noInstaller'), ' ', link(release.html_url, t('releasePage')));

    // macOS and Linux builds, when the release has them
    const more = $('.js-more');
    more.replaceChildren();
    const arm = /arm|aarch64/i.test(navigator.userAgentData?.platform ?? '') || /Mac.*(ARM|Apple)/.test(navigator.userAgent);
    const mac = asset((n) => n.endsWith(`${arm ? 'arm64' : 'x64'}.dmg`)) ?? asset((n) => n.endsWith('.dmg'));
    const linux = asset((n) => n.endsWith('.AppImage'));
    if (state === 'ready') {
      const links = [mac && link(mac.browser_download_url, 'macOS'), linux && link(linux.browser_download_url, 'Linux'), link(releasesPage, t('all'))].filter(Boolean);
      more.append(`${t('also')} `);
      links.forEach((a, i) => more.append(...(i ? [' · ', a] : [a])));
    }
    more.hidden = state !== 'ready';
  }

  function loadRelease() {
    if (!repo) return;
    const key = `medirian-release:${repo}`;
    try {
      const cached = JSON.parse(sessionStorage.getItem(key));
      if (cached && Date.now() - cached.at < 10 * 60 * 1000) {
        download = cached.download;
        renderDownload();
        return;
      }
    } catch {
      /* no cache */
    }
    fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: 'application/vnd.github+json' } })
      .then(async (response) => {
        if (response.status === 404) return { state: 'none', release: null };
        if (!response.ok) return { state: 'unknown', release: null }; // rate limited: the stable link stays
        const data = await response.json();
        const release = {
          tag_name: String(data.tag_name ?? ''),
          html_url: String(data.html_url ?? releasesPage),
          published_at: data.published_at ?? null,
          assets: (data.assets ?? []).map((a) => ({ name: a.name, size: a.size, browser_download_url: a.browser_download_url }))
        };
        return { state: release.assets.some((a) => a.name === INSTALLER) ? 'ready' : 'noInstaller', release };
      })
      .catch(() => ({ state: 'unknown', release: null }))
      .then((result) => {
        download = result;
        if (result.state !== 'unknown') {
          try {
            sessionStorage.setItem(key, JSON.stringify({ at: Date.now(), download: result }));
          } catch {
            /* storage full or blocked */
          }
        }
        renderDownload();
      });
  }

  // ------------------------------------------------------------------ Medirian Services status

  let status = servicesUrl ? 'checking' : 'off';
  let servicesVersion = '';

  function renderStatus() {
    const line = $('.js-status');
    line.hidden = status === 'off';
    line.classList.toggle('is-online', status === 'online');
    line.classList.toggle('is-offline', status === 'offline');
    $('.js-status-text').textContent =
      status === 'online' ? `${t('online')}${servicesVersion ? ` · v${servicesVersion}` : ''}` : status === 'offline' ? t('offline') : t('checking');
  }

  function checkStatus() {
    if (!servicesUrl) return;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 6000);
    fetch(`${servicesUrl}/v1/status`, { signal: controller.signal, cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        status = data?.name === 'medirian-services' ? 'online' : 'offline';
        servicesVersion = typeof data?.version === 'string' ? data.version : '';
      })
      .catch(() => {
        status = 'offline';
      })
      .finally(() => {
        clearTimeout(timer);
        renderStatus();
      });
  }

  // ------------------------------------------------------------------ footer links

  const footer = [
    ['.js-github', repo ? `https://github.com/${repo}` : ''],
    ['.js-discord', https(config.discordUrl)],
    ['.js-privacy', https(config.privacyUrl)],
    ['.js-terms', https(config.termsUrl)]
  ];
  for (const [selector, href] of footer) {
    const a = $(selector);
    a.hidden = !href;
    if (href) {
      a.href = href;
      a.rel = 'noopener';
    }
  }
  $('.js-year').textContent = String(Math.max(2026, new Date().getFullYear()));

  // ------------------------------------------------------------------ navigation

  const nav = $('[data-nav]');
  const toggle = $('.nav__toggle');
  const setOpen = (open) => {
    nav.classList.toggle('is-open', open);
    toggle.setAttribute('aria-expanded', String(open));
  };
  toggle.addEventListener('click', () => setOpen(!nav.classList.contains('is-open')));
  $$('.nav__menu a, .nav__cta, .nav__brand').forEach((a) => a.addEventListener('click', () => setOpen(false)));
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && nav.classList.contains('is-open')) {
      setOpen(false);
      toggle.focus();
    }
  });
  window.matchMedia('(min-width: 861px)').addEventListener('change', (event) => event.matches && setOpen(false));

  // the link of the section on screen is marked
  const navLinks = new Map($$('.nav__menu a').map((a) => [a.getAttribute('href').slice(1), a]));
  const sectionLink = { top: 'top', features: 'features', screenshots: 'features', mods: 'mods', profiles: 'mods', hud: 'features',
    settings: 'features', cosmetics: 'features', why: 'features', download: 'download', faq: 'faq' };
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const current = sectionLink[entry.target.id];
          navLinks.forEach((a, id) => (id === current ? a.setAttribute('aria-current', 'true') : a.removeAttribute('aria-current')));
        }
      },
      { rootMargin: '-45% 0px -50% 0px' }
    );
    $$('main > section[id]').forEach((section) => observer.observe(section));
  }

  // ------------------------------------------------------------------ scroll: solid navigation, hero parallax

  const hero = $('.hero');
  let ticking = false;
  function onScroll() {
    ticking = false;
    const y = window.scrollY;
    nav.classList.toggle('is-solid', y > 8);
    if (!reduceMotion && y <= hero.offsetHeight) hero.style.setProperty('--parallax', y.toFixed(1));
  }
  window.addEventListener(
    'scroll',
    () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(onScroll);
      }
    },
    { passive: true }
  );
  onScroll();

  // ------------------------------------------------------------------ tabs (screenshots, advanced settings)

  function tabs(list) {
    const buttons = $$('[role="tab"]', list);
    const select = (button, focus) => {
      buttons.forEach((b) => {
        const on = b === button;
        b.setAttribute('aria-selected', String(on));
        b.tabIndex = on ? 0 : -1;
        document.getElementById(b.getAttribute('aria-controls')).hidden = !on;
      });
      if (focus) button.focus();
    };
    buttons.forEach((button, i) => {
      button.addEventListener('click', () => select(button, false));
      button.addEventListener('keydown', (event) => {
        const next = { ArrowRight: i + 1, ArrowDown: i + 1, ArrowLeft: i - 1, ArrowUp: i - 1, Home: 0, End: buttons.length - 1 }[event.key];
        if (next === undefined) return;
        event.preventDefault();
        select(buttons[(next + buttons.length) % buttons.length], true);
      });
    });
    select(buttons.find((b) => b.getAttribute('aria-selected') === 'true') ?? buttons[0], false);
  }
  tabs($('.shots__tabs'));
  $('[data-shots]').classList.add('is-tabbed');
  tabs($('.panel__tabs'));

  // ------------------------------------------------------------------ HUD look

  const hudDemo = $('.hud-demo');
  $$('[data-look-set]').forEach((button) =>
    button.addEventListener('click', () => {
      hudDemo.dataset.look = button.dataset.lookSet;
      $$('[data-look-set]').forEach((b) => b.setAttribute('aria-pressed', String(b === button)));
    })
  );

  // ------------------------------------------------------------------ capes

  const capeNames = { medirian: 'Medirian', moonlit: 'Moonlit', aurora: 'Aurora', ember: 'Ember', frost: 'Frost' };
  const capes = $$('[data-cape]');
  const pickCape = (button, focus) => {
    capes.forEach((b) => {
      b.setAttribute('aria-checked', String(b === button));
      b.tabIndex = b === button ? 0 : -1;
    });
    $('.js-cape').src = `assets/capes/${button.dataset.cape}.png`;
    $('.js-cape-name').textContent = capeNames[button.dataset.cape];
    if (focus) button.focus();
  };
  capes.forEach((button, i) => {
    button.addEventListener('click', () => pickCape(button, false));
    button.addEventListener('keydown', (event) => {
      const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
      if (!step) return;
      event.preventDefault();
      pickCape(capes[(i + step + capes.length) % capes.length], true);
    });
  });
  pickCape(capes.find((b) => b.getAttribute('aria-checked') === 'true') ?? capes[0], false);

  // ------------------------------------------------------------------ section headings fade in once

  if ('IntersectionObserver' in window && !reduceMotion) {
    const reveal = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-in');
            reveal.unobserve(entry.target);
          }
        }
      },
      { rootMargin: '0px 0px -10% 0px' }
    );
    $$('.section__head, .split__text').forEach((el) => {
      el.classList.add('reveal');
      reveal.observe(el);
    });
  }

  // ------------------------------------------------------------------ start

  if (lang === 'pl') setLanguage('pl');
  else applyLanguage();
  loadRelease();
  checkStatus();
})();
