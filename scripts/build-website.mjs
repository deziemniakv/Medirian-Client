#!/usr/bin/env node
// Builds the Medirian Client website (website/) into a folder ready for GitHub Pages or any static host:
// the owner's configuration goes into the page, and the result is checked — every file a page refers to
// exists, every #link has its target, every image has alt text and its real size, every text has a
// Polish translation, the Minecraft versions and the module count match the code, and nothing is left
// over from a template. Any problem fails the build.
//
// Usage: node scripts/build-website.mjs [--out <dir>] [--external]
//   --out       output folder (default: distribution/website)
//   --external  also request every external link and report the ones that do not answer
//
// Configuration (the process environment, or .env in the repository root; docs/OWNER_SETUP.md, "Website"):
//   GITHUB_REPOSITORY        owner/repo whose GitHub Releases the download buttons use (GitHub Actions sets it;
//                            MEDIRIAN_WEBSITE_REPO overrides it)
//   MEDIRIAN_SERVICES_URL    Medirian Services, for the status line (GET /v1/status)
//   MEDIRIAN_DISCORD_URL     a Discord invite (https://discord.gg/…), for the footer
//   MEDIRIAN_PRIVACY_URL     the privacy policy, for the footer
//   MEDIRIAN_TERMS_URL       the terms of use, for the footer
//   MEDIRIAN_SITE_URL        the site's own address (https://…/), for link previews; the Pages workflow sets it
// Empty values hide what depends on them.
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import { parseEnv } from './check-env.mjs';
import { MODULE_ICONS } from './pixel/icons.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SOURCE = join(ROOT, 'website');
const INSTALLER = 'MedirianClientSetup.exe';
/** Not published: notes for whoever maintains the site. */
const SKIP = new Set(['README.md']);
/** Everything a visitor downloads, and the first screen (HTML, CSS, JS, font, hero art). */
const BUDGET = { total: 1_500_000, file: 300_000, firstScreen: 250_000 };

const placeholder = (v) => /YOUR_|your-domain|\.example\b|OWNER\/REPO/.test(v);

/** The owner's values for the site → { config, problems }. */
export function siteConfig(env) {
  const problems = [];
  const https = (key, { host } = {}) => {
    const value = (env[key] ?? '').trim();
    if (!value) return '';
    let parsed;
    try {
      parsed = new URL(value);
    } catch {
      problems.push(`${key}: not a valid URL`);
      return '';
    }
    if (placeholder(value)) problems.push(`${key}: still the placeholder from .env.example`);
    else if (parsed.protocol !== 'https:') problems.push(`${key}: must use HTTPS`);
    else if (host && !host.test(parsed.hostname)) problems.push(`${key}: expected ${host.source.replace(/\\/g, '')}`);
    else return value.replace(/\/+$/, '');
    return '';
  };
  const repo = (env.MEDIRIAN_WEBSITE_REPO || env.GITHUB_REPOSITORY || '').trim();
  if (repo && (!/^[\w.-]+\/[\w.-]+$/.test(repo) || placeholder(repo))) problems.push(`repository "${repo}": expected owner/repo`);
  const siteUrl = https('MEDIRIAN_SITE_URL');
  return {
    config: {
      repo: problems.some((p) => p.startsWith('repository')) ? '' : repo,
      servicesUrl: https('MEDIRIAN_SERVICES_URL'),
      discordUrl: https('MEDIRIAN_DISCORD_URL', { host: /^(discord\.gg|(www\.)?discord\.com)$/ }),
      privacyUrl: https('MEDIRIAN_PRIVACY_URL'),
      termsUrl: https('MEDIRIAN_TERMS_URL')
    },
    siteUrl: siteUrl ? `${siteUrl}/` : '',
    problems
  };
}

const escapeAttr = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');

/** index.html with the configuration filled in. */
export function configure(html, { config, siteUrl }) {
  const json = JSON.stringify(config).replace(/</g, '\\u003c');
  let out = html.replace(/(<script type="application\/json" id="medirian-config">)[\s\S]*?(<\/script>)/, (_, a, b) => a + json + b);
  if (config.repo) {
    // without JavaScript the buttons still lead to the newest installer
    const stable = `https://github.com/${config.repo}/releases/latest/download/${INSTALLER}`;
    out = out.replace(/(<a class="[^"]*\bjs-installer\b[^"]*" href=")[^"]*(")/g, (_, a, b) => a + escapeAttr(stable) + b);
  }
  // link previews need absolute addresses
  out = siteUrl
    ? out.replace(/<meta property="og:image" content="([^"]+)" data-site-url>/, (_, path) =>
        `<meta property="og:image" content="${escapeAttr(new URL(path, siteUrl).href)}">\n  <meta property="og:url" content="${escapeAttr(siteUrl)}">\n  <link rel="canonical" href="${escapeAttr(siteUrl)}">`)
    : out.replace(/\s*<meta property="og:image" content="[^"]+" data-site-url>/, '');
  return out;
}

function files(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? files(join(dir, e.name)) : [join(dir, e.name)]));
}

/** Width and height of a PNG or WebP file, or null. */
export function imageSize(buffer) {
  if (buffer.subarray(1, 4).toString('latin1') === 'PNG') return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
  if (buffer.subarray(0, 4).toString('latin1') !== 'RIFF' || buffer.subarray(8, 12).toString('latin1') !== 'WEBP') return null;
  const chunk = buffer.subarray(12, 16).toString('latin1');
  if (chunk === 'VP8L') {
    const b = buffer.readUInt32LE(21);
    return { width: (b & 0x3fff) + 1, height: ((b >> 14) & 0x3fff) + 1 };
  }
  if (chunk === 'VP8 ') return { width: buffer.readUInt16LE(26) & 0x3fff, height: buffer.readUInt16LE(28) & 0x3fff };
  if (chunk === 'VP8X') return { width: buffer.readUIntLE(24, 3) + 1, height: buffer.readUIntLE(27, 3) + 1 };
  return null;
}

const attr = (tag, name) => new RegExp(`\\s${name}="([^"]*)"`).exec(tag)?.[1];
const textOf = (html) => html.replace(/<[^>]+>/g, ' ').replace(/&amp;/g, '&').replace(/\s+/g, ' ');

/** Checks the built site in {@code dir} → a list of problems (empty: fine). */
export function checkSite(dir) {
  const problems = [];
  const html = readFileSync(join(dir, 'index.html'), 'utf8');
  const css = ['style.css', 'assets/icons.css'].map((f) => [f, readFileSync(join(dir, f), 'utf8')]);
  const exists = (path) => existsSync(join(dir, path.split(/[?#]/)[0]));

  // ids and #links
  const ids = [...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]);
  const seen = new Set();
  for (const id of ids) {
    if (seen.has(id)) problems.push(`index.html: the id "${id}" is used twice`);
    seen.add(id);
  }
  for (const [, target] of html.matchAll(/\shref="#([^"]*)"/g)) {
    if (target && !seen.has(target)) problems.push(`index.html: link to #${target}, but nothing has that id`);
  }
  for (const [, ref] of html.matchAll(/\saria-(?:controls|labelledby)="([^"]+)"/g)) {
    if (!seen.has(ref)) problems.push(`index.html: aria reference to "${ref}", but nothing has that id`);
  }

  // local files
  const local = (ref) => ref && !/^(https?:|mailto:|#|data:)/.test(ref);
  for (const [, ref] of html.matchAll(/\s(?:src|href|content)="([^"]+)"/g)) {
    if (local(ref) && /\.\w{2,5}$/.test(ref) && !exists(ref)) problems.push(`index.html: ${ref} does not exist`);
  }
  for (const [file, text] of css) {
    for (const [, ref] of text.matchAll(/url\(['"]?([^'")]+)['"]?\)/g)) {
      if (local(ref) && !existsSync(join(dir, dirname(file), ref))) problems.push(`${file}: ${ref} does not exist`);
    }
  }
  const js = readFileSync(join(dir, 'app.js'), 'utf8');
  for (const [, ref] of js.matchAll(/['"`]((?:assets\/)[^'"`$]+\.\w+)['"`]/g)) {
    if (!exists(ref)) problems.push(`app.js: ${ref} does not exist`);
  }
  for (const [, cape] of html.matchAll(/data-cape="([^"]+)"/g)) {
    if (!exists(`assets/capes/${cape}.png`)) problems.push(`index.html: no cape image for "${cape}"`);
  }
  for (const [, icon] of html.matchAll(/\bico--([\w-]+)/g)) {
    if (!css[1][1].includes(`.ico--${icon}{`)) problems.push(`index.html: no icon "${icon}" in assets/icons.css`);
  }

  // images: alt text, and width/height with the real aspect ratio (no layout shift, nothing stretched)
  for (const [tag] of html.matchAll(/<img\b[^>]*>/g)) {
    const src = attr(tag, 'src');
    if (attr(tag, 'alt') === undefined) problems.push(`index.html: <img src="${src}"> has no alt attribute`);
    const width = Number(attr(tag, 'width'));
    const height = Number(attr(tag, 'height'));
    if (!width || !height) {
      problems.push(`index.html: <img src="${src}"> needs width and height`);
      continue;
    }
    if (!local(src) || !exists(src)) continue;
    const size = imageSize(readFileSync(join(dir, src)));
    if (size && Math.abs(size.width / size.height - width / height) > 0.01) {
      problems.push(`index.html: <img src="${src}"> says ${width}×${height}, the file is ${size.width}×${size.height}`);
    }
  }

  // translations: every text of the page has a Polish version, and the dictionary has nothing extra
  const sandbox = { window: {} };
  vm.runInNewContext(readFileSync(join(dir, 'pl.js'), 'utf8'), sandbox);
  const pl = sandbox.window.MEDIRIAN_PL ?? {};
  const keys = new Set([...html.matchAll(/\sdata-t(?:-label)?="([^"]+)"/g)].map((m) => m[1]));
  keys.add('meta.title');
  for (const key of keys) if (!(key in pl)) problems.push(`pl.js: no Polish text for "${key}"`);
  for (const key of Object.keys(pl)) if (!keys.has(key)) problems.push(`pl.js: "${key}" is not used on the page`);

  // facts that must match the code
  const versions = [...readFileSync(join(ROOT, 'scripts/build-clients.mjs'), 'utf8').matchAll(/minecraftVersion: '([^']+)'/g)].map((m) => m[1]);
  const heroMeta = /data-t="hero\.meta">([^<]*)</.exec(html)?.[1] ?? '';
  for (const version of versions) {
    if (!heroMeta.includes(version)) problems.push(`index.html: hero.meta does not list Minecraft ${version} (scripts/build-clients.mjs)`);
    if (!pl['hero.meta']?.includes(version)) problems.push(`pl.js: hero.meta does not list Minecraft ${version}`);
  }
  for (const [file, text] of [['index.html', textOf(html)], ['pl.js', Object.values(pl).join(' ')]]) {
    for (const [, n] of text.matchAll(/\b(\d+) (?:modules|modułów|modułami)\b/g)) {
      if (Number(n) !== MODULE_ICONS.length) problems.push(`${file}: says ${n} modules, the client has ${MODULE_ICONS.length}`);
    }
  }

  // nothing left over from a template or a draft
  for (const file of ['index.html', 'app.js', 'pl.js', 'style.css']) {
    const text = readFileSync(join(dir, file), 'utf8');
    for (const word of ['lorem ipsum', 'TODO', 'FIXME', 'OWNER/REPO', 'YOUR_', 'example.com', 'href="#"']) {
      if (word === 'href="#"' && file === 'index.html') continue; // the footer's hidden links, filled in by app.js
      if (text.includes(word)) problems.push(`${file}: contains "${word}"`);
    }
  }

  // size
  let total = 0;
  for (const file of files(dir)) {
    const size = statSync(file).size;
    total += size;
    if (size > BUDGET.file) problems.push(`${relative(dir, file)} is ${Math.round(size / 1024)} KB (limit ${BUDGET.file / 1000} KB per file)`);
  }
  if (total > BUDGET.total) problems.push(`the site is ${Math.round(total / 1024)} KB (limit ${BUDGET.total / 1000} KB)`);
  const first = ['index.html', 'style.css', 'assets/icons.css', 'app.js', 'assets/medirian-pixel.ttf', 'assets/logo.png', 'assets/icons.png',
    ...['sky', 'far', 'forest', 'fog', 'ground'].map((n) => `assets/scene/${n}.png`)];
  const firstScreen = first.reduce((sum, f) => sum + statSync(join(dir, f)).size, 0);
  if (firstScreen > BUDGET.firstScreen) problems.push(`the first screen loads ${Math.round(firstScreen / 1024)} KB (limit ${BUDGET.firstScreen / 1000} KB)`);

  return { problems, total, firstScreen };
}

/** External links of the page (https://…), for --external. */
export function externalLinks(html) {
  return [...new Set([...html.matchAll(/\shref="(https:\/\/[^"]+)"/g)].map((m) => m[1].replace(/&amp;/g, '&')))];
}

export function buildWebsite({ out, env }) {
  const site = siteConfig(env);
  rmSync(out, { recursive: true, force: true });
  mkdirSync(out, { recursive: true });
  cpSync(SOURCE, out, { recursive: true, filter: (src) => !SKIP.has(relative(SOURCE, src)) });
  const index = join(out, 'index.html');
  writeFileSync(index, configure(readFileSync(index, 'utf8'), site));
  const checked = checkSite(out);
  return { ...site, ...checked, problems: [...site.problems, ...checked.problems] };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const outArg = args.indexOf('--out');
  const out = resolve(outArg >= 0 ? args[outArg + 1] : join(ROOT, 'distribution/website'));
  const envFile = join(ROOT, '.env');
  const env = { ...(existsSync(envFile) ? parseEnv(readFileSync(envFile, 'utf8')) : {}) };
  for (const key of ['GITHUB_REPOSITORY', 'MEDIRIAN_WEBSITE_REPO', 'MEDIRIAN_SERVICES_URL', 'MEDIRIAN_DISCORD_URL', 'MEDIRIAN_PRIVACY_URL',
    'MEDIRIAN_TERMS_URL', 'MEDIRIAN_SITE_URL']) {
    if (process.env[key] !== undefined) env[key] = process.env[key];
  }
  const result = buildWebsite({ out, env });
  const shown = (v) => v || '— (hidden)';
  console.log(`Website → ${relative(process.cwd(), out) || '.'}`);
  console.log(`  releases       ${result.config.repo ? `github.com/${result.config.repo}` : '— (download buttons say "Coming soon")'}`);
  console.log(`  services       ${shown(result.config.servicesUrl)}`);
  console.log(`  discord        ${shown(result.config.discordUrl)}`);
  console.log(`  privacy, terms ${shown(result.config.privacyUrl)}, ${shown(result.config.termsUrl)}`);
  console.log(`  site address   ${result.siteUrl || '— (no link-preview image)'}`);
  console.log(`  size           ${Math.round(result.total / 1024)} KB, first screen ${Math.round(result.firstScreen / 1024)} KB`);

  let failed = result.problems.length > 0;
  if (args.includes('--external')) {
    const links = externalLinks(readFileSync(join(out, 'index.html'), 'utf8'));
    for (const link of links) {
      let status;
      try {
        status = (await fetch(link, { method: 'GET', redirect: 'follow', signal: AbortSignal.timeout(15000) })).status;
      } catch (error) {
        status = error.message;
      }
      const ok = typeof status === 'number' && status < 400;
      console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${status} ${link}`);
      failed ||= !ok;
    }
  }
  for (const problem of result.problems) console.log(`  PROBLEM ${problem}`);
  if (failed) process.exit(1);
  console.log('  no problems found');
}
