#!/usr/bin/env node
// Checks the owner's configuration (.env in the repository root, or the file given, plus the process
// environment, which wins — that is how GitHub Actions passes Variables and Secrets) before a release
// build: what is set, what is missing, what is wrong — without ever printing a secret.
//
// Usage: node scripts/check-env.mjs [path/to/.env] [--release]
//   --release  also require everything a public release needs (exit code 1 when something is missing):
//              Microsoft login, Medirian Services, the Modrinth contact and Windows code signing
//              (a certificate file or Azure Artifact Signing; MEDIRIAN_ALLOW_UNSIGNED=1 allows an
//              unsigned test release).
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** KEY=value lines; # comments; optional quotes. */
export function parseEnv(text) {
  const env = {};
  for (const line of text.split(/\r?\n/)) {
    const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/.exec(line);
    if (match && !line.trimStart().startsWith('#')) {
      env[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
    }
  }
  return env;
}

const LOOPBACK = new Set(['localhost', '127.0.0.1', '[::1]']);
const placeholder = (v) => /YOUR_|your-domain|\.example\b/.test(v);
const url = (release) => (v) => {
  try {
    const parsed = new URL(v);
    if (placeholder(v)) return 'still the placeholder from .env.example';
    if (parsed.protocol === 'https:') return null;
    if (parsed.protocol === 'http:' && LOOPBACK.has(parsed.hostname)) return release ? 'http:// is only for local testing' : null;
    return 'must use HTTPS';
  } catch {
    return 'not a valid URL';
  }
};
const certificate = (v) => (/^(https:\/\/|[A-Za-z0-9+/=]{200,}$)/.test(v) || existsSync(resolve(root, v))) ? null : 'neither a file, an https URL nor base64';
const filled = (v) => placeholder(v) ? 'still the placeholder' : null;
const GUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** [name, kind, what it is for, check(value) → problem or null] */
export function checks(release) {
  return [
    ['MEDIRIAN_MSA_CLIENT_ID', 'public', 'Microsoft login', (v) => placeholder(v) ? 'still the placeholder' : GUID.test(v) ? null : 'must be the Application (client) ID (a GUID)'],
    ['MEDIRIAN_DISCORD_APP_ID', 'public', 'Discord Rich Presence', (v) => /^\d{17,20}$/.test(v) ? null : 'must be the numeric Application ID'],
    ['MEDIRIAN_SERVICES_URL', 'public', 'Medirian Services', url(release)],
    ['MEDIRIAN_MANIFEST_URL', 'public', 'release channel (the release workflow sets it on GitHub)', url(release)],
    ['MEDIRIAN_CONTACT', 'public', 'Modrinth User-Agent contact', filled],
    ['WIN_CSC_LINK', 'secret', 'Windows signing: certificate file (.pfx)', certificate],
    ['WIN_CSC_KEY_PASSWORD', 'secret', 'Windows signing: certificate password', () => null],
    ['AZURE_SIGNING_ENDPOINT', 'build', 'Windows signing: Azure Artifact Signing endpoint', url(release)],
    ['AZURE_SIGNING_ACCOUNT', 'build', 'Windows signing: Artifact Signing account', filled],
    ['AZURE_SIGNING_PROFILE', 'build', 'Windows signing: certificate profile', filled],
    ['AZURE_SIGNING_PUBLISHER', 'build', 'Windows signing: publisher name (CN of the certificate)', filled],
    ['AZURE_TENANT_ID', 'secret', 'Windows signing: Azure tenant', (v) => GUID.test(v) ? null : 'must be a GUID'],
    ['AZURE_CLIENT_ID', 'secret', 'Windows signing: Azure app (service principal)', (v) => GUID.test(v) ? null : 'must be a GUID'],
    ['AZURE_CLIENT_SECRET', 'secret', 'Windows signing: Azure app secret', () => null],
    ['CSC_LINK', 'secret', 'macOS signing: Developer ID Application certificate (.p12)', certificate],
    ['CSC_KEY_PASSWORD', 'secret', 'macOS signing: certificate password', () => null],
    ['MEDIRIAN_SERVICES_DOMAIN', 'server', 'domain for HTTPS (Caddy)', (v) => placeholder(v) ? 'still the placeholder' : /^[a-z0-9.-]+\.[a-z]{2,}$/i.test(v) ? null : 'must be a domain name like api.example.org']
  ];
}

const AZURE = ['AZURE_SIGNING_ENDPOINT', 'AZURE_SIGNING_ACCOUNT', 'AZURE_SIGNING_PROFILE', 'AZURE_SIGNING_PUBLISHER',
  'AZURE_TENANT_ID', 'AZURE_CLIENT_ID', 'AZURE_CLIENT_SECRET'];

/** How Windows releases get signed with {@code env}: 'certificate', 'azure', or null (unsigned). */
export function windowsSigning(env) {
  const has = (key) => !!(env[key] ?? '').trim();
  if (has('WIN_CSC_LINK') && has('WIN_CSC_KEY_PASSWORD')) return 'certificate';
  if (AZURE.every(has)) return 'azure';
  return null;
}

/** Every value with its state ('ok' | 'off' | 'fix') and problem; release problems that span values last. */
export function checkEnv(env, release) {
  const rows = [];
  for (const [key, kind, what, check] of checks(release)) {
    const value = (env[key] ?? '').trim();
    let problem = value ? check(value) : null;
    if (!value && release && ['MEDIRIAN_MSA_CLIENT_ID', 'MEDIRIAN_SERVICES_URL', 'MEDIRIAN_CONTACT'].includes(key)) {
      problem = 'required for a release';
    }
    rows.push({ key, kind, what, value, state: problem ? 'fix' : value ? 'ok' : 'off', problem });
  }
  const signing = windowsSigning(env);
  const partialAzure = !signing && AZURE.some((key) => (env[key] ?? '').trim());
  if (partialAzure) {
    rows.push({ key: 'Windows code signing', kind: '', what: '', value: '', state: 'fix', problem: 'Azure Artifact Signing needs all seven AZURE_* values' });
  } else if (release && !signing && env.MEDIRIAN_ALLOW_UNSIGNED !== '1') {
    rows.push({ key: 'Windows code signing', kind: '', what: '', value: '', state: 'fix',
      problem: 'a public release must be signed: WIN_CSC_LINK + WIN_CSC_KEY_PASSWORD, or Azure Artifact Signing (MEDIRIAN_ALLOW_UNSIGNED=1 for a test release)' });
  }
  return { rows, signing };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const release = args.includes('--release');
  const file = resolve(args.find((a) => !a.startsWith('--')) ?? join(root, '.env'));
  const env = existsSync(file) ? parseEnv(readFileSync(file, 'utf8')) : {};
  if (!existsSync(file) && !process.env.CI) {
    console.log(`No ${file} — copy .env.example to .env first (values from the environment are still checked).`);
  }
  for (const [key] of checks(release)) {
    if (process.env[key]) env[key] = process.env[key];
  }
  if (process.env.MEDIRIAN_ALLOW_UNSIGNED) env.MEDIRIAN_ALLOW_UNSIGNED = process.env.MEDIRIAN_ALLOW_UNSIGNED;
  const { rows, signing } = checkEnv(env, release);
  for (const row of rows) {
    const shown = !row.value ? '—' : row.kind === 'secret' ? '(set, hidden)' : row.value;
    console.log(`${row.state.toUpperCase().padEnd(4)} ${row.key.padEnd(26)} ${row.kind.padEnd(6)} ${row.what}${row.problem ? ` — ${row.problem}` : ''}` +
      (row.kind ? `\n       ${shown}` : ''));
  }
  console.log(`\nWindows releases will be ${signing === 'certificate' ? 'signed with the certificate file' : signing === 'azure' ? 'signed with Azure Artifact Signing' : 'UNSIGNED'}.`);
  if (rows.some((row) => row.state === 'fix')) {
    console.log('Some values need attention (docs/OWNER_SETUP.md).');
    process.exit(1);
  }
  console.log(release ? 'Ready for a release build.' : 'No problems found.');
}
