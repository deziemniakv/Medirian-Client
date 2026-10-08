import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkEnv, parseEnv, windowsSigning } from '../check-env.mjs';

const READY = {
  MEDIRIAN_MSA_CLIENT_ID: '0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0',
  MEDIRIAN_SERVICES_URL: 'https://api.medirian.net',
  MEDIRIAN_CONTACT: 'owner@medirian.net'
};
const fixes = (result) => result.rows.filter((row) => row.state === 'fix').map((row) => row.key);

test('.env lines: comments, quotes, empty values', () => {
  assert.deepEqual(parseEnv('# c\nA=1\nB="two words"\n  C = \'x\' \nD=\n#E=5'), { A: '1', B: 'two words', C: 'x', D: '' });
});

test('placeholders and plain http are refused', () => {
  const result = checkEnv({ MEDIRIAN_MSA_CLIENT_ID: 'YOUR_AZURE_APPLICATION_CLIENT_ID', MEDIRIAN_SERVICES_URL: 'http://api.medirian.net' }, false);
  assert.deepEqual(fixes(result), ['MEDIRIAN_MSA_CLIENT_ID', 'MEDIRIAN_SERVICES_URL']);
  assert.deepEqual(fixes(checkEnv({ MEDIRIAN_SERVICES_URL: 'http://127.0.0.1:8080' }, false)), [], 'loopback http is fine for testing');
  assert.deepEqual(fixes(checkEnv({ ...READY, MEDIRIAN_SERVICES_URL: 'http://127.0.0.1:8080', MEDIRIAN_ALLOW_UNSIGNED: '1' }, true)),
    ['MEDIRIAN_SERVICES_URL'], 'but not for a release');
});

test('a release needs login, services, contact and Windows signing', () => {
  assert.deepEqual(fixes(checkEnv({}, true)), ['MEDIRIAN_MSA_CLIENT_ID', 'MEDIRIAN_SERVICES_URL', 'MEDIRIAN_CONTACT', 'Windows code signing']);
  assert.deepEqual(fixes(checkEnv({ ...READY, MEDIRIAN_ALLOW_UNSIGNED: '1' }, true)), [], 'an unsigned test release when asked for');
  assert.deepEqual(fixes(checkEnv({ ...READY, WIN_CSC_LINK: 'A'.repeat(300), WIN_CSC_KEY_PASSWORD: 'x' }, true)), []);
});

test('Windows signing: a certificate file or all of Azure Trusted Signing', () => {
  assert.equal(windowsSigning({ WIN_CSC_LINK: 'cert.pfx', WIN_CSC_KEY_PASSWORD: 'x' }), 'certificate');
  const azure = {
    AZURE_SIGNING_ENDPOINT: 'https://weu.codesigning.azure.net/', AZURE_SIGNING_ACCOUNT: 'medirian', AZURE_SIGNING_PROFILE: 'public',
    AZURE_SIGNING_PUBLISHER: 'Medirian', AZURE_TENANT_ID: '0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0',
    AZURE_CLIENT_ID: '0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f1', AZURE_CLIENT_SECRET: 's'
  };
  assert.equal(windowsSigning(azure), 'azure');
  assert.equal(windowsSigning({ ...azure, AZURE_CLIENT_SECRET: '' }), null);
  assert.deepEqual(fixes(checkEnv({ ...READY, AZURE_SIGNING_ENDPOINT: 'https://weu.codesigning.azure.net/' }, false)), ['Windows code signing'],
    'half of the Azure values is a mistake, not "unsigned"');
});
