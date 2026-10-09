import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { buildWebsite, configure, imageSize, siteConfig } from '../build-website.mjs';
import { encodePng } from '../lib/png.mjs';

const config = (html) => JSON.parse(/id="medirian-config">([^<]*)</.exec(html)[1]);

test('the website builds without problems, unconfigured and configured', () => {
  const out = mkdtempSync(join(tmpdir(), 'medirian-site-'));
  try {
    const plain = buildWebsite({ out, env: {} });
    assert.deepEqual(plain.problems, []);
    assert.equal(config(readFileSync(join(out, 'index.html'), 'utf8')).repo, '');

    const result = buildWebsite({
      out,
      env: {
        GITHUB_REPOSITORY: 'someone/Medirian-Client',
        MEDIRIAN_SERVICES_URL: 'https://api.medirian.test/',
        MEDIRIAN_DISCORD_URL: 'https://discord.gg/abc123',
        MEDIRIAN_SITE_URL: 'https://someone.github.io/Medirian-Client'
      }
    });
    assert.deepEqual(result.problems, []);
    const html = readFileSync(join(out, 'index.html'), 'utf8');
    assert.deepEqual(config(html), {
      repo: 'someone/Medirian-Client',
      servicesUrl: 'https://api.medirian.test',
      discordUrl: 'https://discord.gg/abc123',
      privacyUrl: '',
      termsUrl: ''
    });
    // the download buttons work without JavaScript
    assert.match(html, /js-installer" href="https:\/\/github\.com\/someone\/Medirian-Client\/releases\/latest\/download\/MedirianClientSetup\.exe"/);
    assert.match(html, /og:image" content="https:\/\/someone\.github\.io\/Medirian-Client\/assets\/social\.png"/);
    assert.ok(!html.includes('data-site-url'));
  } finally {
    rmSync(out, { recursive: true, force: true });
  }
});

test('bad configuration values are reported, not published', () => {
  const { config: c, problems } = siteConfig({
    GITHUB_REPOSITORY: 'not a repo',
    MEDIRIAN_SERVICES_URL: 'http://api.medirian.test',
    MEDIRIAN_DISCORD_URL: 'https://evil.test/invite',
    MEDIRIAN_PRIVACY_URL: 'https://your-domain.example/privacy'
  });
  assert.equal(problems.length, 4);
  assert.deepEqual(c, { repo: '', servicesUrl: '', discordUrl: '', privacyUrl: '', termsUrl: '' });
});

test('configuration cannot break out of the page', () => {
  const html = '<script type="application/json" id="medirian-config">{}</script>';
  const out = configure(html, { config: { repo: '', servicesUrl: 'https://x.test/</script><script>alert(1)</script>' }, siteUrl: '' });
  assert.ok(!out.includes('</script><script>'));
});

test('image sizes are read from PNG and WebP headers', () => {
  assert.deepEqual(imageSize(encodePng({ width: 3, height: 2, data: Buffer.alloc(24) })), { width: 3, height: 2 });
  const webp = readFileSync(new URL('../../website/assets/screens/main-menu.webp', import.meta.url));
  assert.deepEqual(imageSize(webp), { width: 1600, height: 900 });
});
