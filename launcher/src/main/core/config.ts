/**
 * The owner's configuration: one `.env` file in the repository root (template: `.env.example`,
 * guide: docs/OWNER_SETUP.md). The launcher build embeds only the PUBLIC values listed here, so a
 * secret that happens to be in `.env` (signing certificates, server settings) never reaches the
 * app. Everything here is readable by anyone who installs Medirian Client — ids and addresses,
 * never keys or passwords.
 *
 * Development runs (`npm run dev`, not packaged) additionally honour the same variables from the
 * process environment, e.g. to point a test run at a local Medirian Services.
 */

export const PUBLIC_CONFIG_KEYS = [
  /** Azure application (client) id for "Sign in with Microsoft". */
  'MEDIRIAN_MSA_CLIENT_ID',
  /** Discord application id for Rich Presence. */
  'MEDIRIAN_DISCORD_APP_ID',
  /** Medirian Services base URL (HTTPS), also passed to the game. */
  'MEDIRIAN_SERVICES_URL',
  /** release-manifest.json of the stable channel (HTTPS). */
  'MEDIRIAN_MANIFEST_URL',
  /** How Modrinth can reach the owner (e-mail or URL), part of the User-Agent Modrinth asks for. */
  'MEDIRIAN_CONTACT'
] as const;

export type PublicConfigKey = (typeof PUBLIC_CONFIG_KEYS)[number];
export type BuildConfig = Record<PublicConfigKey, string>;

export interface OwnerConfig {
  msaClientId: string;
  discordAppId: string;
  servicesUrl: string;
  manifestUrl: string;
  contact: string;
  /** Values that were set but rejected (for example an http:// address), for the log and Settings → About. */
  problems: string[];
}

const LOOPBACK = new Set(['localhost', '127.0.0.1', '[::1]', '::1']);

/**
 * Checks an address: HTTPS, or plain HTTP to this computer (local testing). Returns the URL
 * without a trailing slash, or a problem.
 */
export function checkUrl(name: string, value: string): { url: string } | { problem: string } {
  const trimmed = value.trim().replace(/\/+$/, '');
  if (!trimmed) {
    return { url: '' };
  }
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return { problem: `${name} is not a valid URL` };
  }
  if (/YOUR_/.test(trimmed) || parsed.hostname.endsWith('.example')) {
    return { problem: `${name} is still the placeholder from .env.example` };
  }
  if (parsed.protocol === 'https:') {
    return { url: trimmed };
  }
  if (parsed.protocol === 'http:' && LOOPBACK.has(parsed.hostname)) {
    return { url: trimmed };
  }
  return { problem: `${name} must use HTTPS (got ${parsed.protocol}//${parsed.host})` };
}

/** Builds the configuration from the embedded values (and, in development, the environment). */
export function resolveOwnerConfig(build: Partial<BuildConfig>, env: Record<string, string | undefined> | null): OwnerConfig {
  const value = (key: PublicConfigKey) => (env?.[key]?.trim() || build[key]?.trim() || '');
  const problems: string[] = [];
  const url = (key: PublicConfigKey) => {
    const result = checkUrl(key, value(key));
    if ('problem' in result) {
      problems.push(result.problem);
      return '';
    }
    return result.url;
  };
  const appId = value('MEDIRIAN_DISCORD_APP_ID');
  if (appId && !/^\d{17,20}$/.test(appId)) {
    problems.push('MEDIRIAN_DISCORD_APP_ID must be the numeric Application ID');
  }
  const clientId = value('MEDIRIAN_MSA_CLIENT_ID');
  if (clientId && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(clientId)) {
    problems.push('MEDIRIAN_MSA_CLIENT_ID must be the Application (client) ID, a GUID');
  }
  return {
    msaClientId: problems.some((p) => p.startsWith('MEDIRIAN_MSA_CLIENT_ID')) ? '' : clientId,
    discordAppId: problems.some((p) => p.startsWith('MEDIRIAN_DISCORD_APP_ID')) ? '' : appId,
    servicesUrl: url('MEDIRIAN_SERVICES_URL'),
    manifestUrl: url('MEDIRIAN_MANIFEST_URL'),
    contact: value('MEDIRIAN_CONTACT'),
    problems
  };
}

let current: OwnerConfig | null = null;

/** The configuration of this launcher build. */
export function ownerConfig(packaged: boolean): OwnerConfig {
  if (!current) {
    const build = typeof __MEDIRIAN_CONFIG__ === 'undefined' ? {} : __MEDIRIAN_CONFIG__;
    current = resolveOwnerConfig(build, packaged ? null : process.env);
  }
  return current;
}
