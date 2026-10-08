import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkUrl, resolveOwnerConfig } from '../src/main/core/config.ts';

test('production addresses must use HTTPS; plain HTTP only reaches this computer', () => {
  assert.deepEqual(checkUrl('X', 'https://api.medirian.dev/'), { url: 'https://api.medirian.dev' });
  assert.deepEqual(checkUrl('X', 'http://127.0.0.1:8080'), { url: 'http://127.0.0.1:8080' });
  assert.deepEqual(checkUrl('X', 'http://localhost:8080/'), { url: 'http://localhost:8080' });
  assert.match((checkUrl('X', 'http://api.medirian.dev') as { problem: string }).problem, /must use HTTPS/);
  assert.match((checkUrl('X', 'ftp://files.medirian.dev') as { problem: string }).problem, /must use HTTPS/);
  assert.match((checkUrl('X', 'not a url') as { problem: string }).problem, /not a valid URL/);
  assert.deepEqual(checkUrl('X', '  '), { url: '' });
});

test('values copied unchanged from .env.example are rejected', () => {
  assert.match((checkUrl('X', 'https://api.your-domain.example') as { problem: string }).problem, /placeholder/);
  const config = resolveOwnerConfig({
    MEDIRIAN_MSA_CLIENT_ID: 'YOUR_AZURE_APPLICATION_CLIENT_ID',
    MEDIRIAN_DISCORD_APP_ID: 'YOUR_DISCORD_APPLICATION_ID',
    MEDIRIAN_SERVICES_URL: 'https://api.your-domain.example'
  }, null);
  assert.equal(config.msaClientId, '');
  assert.equal(config.discordAppId, '');
  assert.equal(config.servicesUrl, '');
  assert.equal(config.problems.length, 3);
});

test('the build values are used; the environment only wins in development', () => {
  const build = {
    MEDIRIAN_MSA_CLIENT_ID: '0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0',
    MEDIRIAN_DISCORD_APP_ID: '123456789012345678',
    MEDIRIAN_SERVICES_URL: 'https://api.medirian.dev',
    MEDIRIAN_MANIFEST_URL: 'https://github.com/medirian/client/releases/latest/download/release-manifest.json',
    MEDIRIAN_CONTACT: 'owner@medirian.dev'
  };
  const packaged = resolveOwnerConfig(build, null);
  assert.deepEqual({ ...packaged, problems: undefined }, {
    msaClientId: build.MEDIRIAN_MSA_CLIENT_ID,
    discordAppId: build.MEDIRIAN_DISCORD_APP_ID,
    servicesUrl: build.MEDIRIAN_SERVICES_URL,
    manifestUrl: build.MEDIRIAN_MANIFEST_URL,
    contact: build.MEDIRIAN_CONTACT,
    problems: undefined
  });
  const dev = resolveOwnerConfig(build, { MEDIRIAN_SERVICES_URL: 'http://127.0.0.1:18080' });
  assert.equal(dev.servicesUrl, 'http://127.0.0.1:18080');
  assert.equal(dev.msaClientId, build.MEDIRIAN_MSA_CLIENT_ID);
});
