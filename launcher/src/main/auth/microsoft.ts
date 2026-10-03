import { USER_AGENT } from '../net/http';

/**
 * Microsoft account → Minecraft authentication (device code flow).
 *
 * MSA device code → MSA token → Xbox Live user token → XSTS token → Minecraft Services token →
 * entitlement (ownership) check → Minecraft profile.
 *
 * Requires an Azure application id that Mojang approved for the Minecraft Services API
 * (see docs/ARCHITECTURE.md §2.6).
 */

const MSA = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';
const SCOPE = 'XboxLive.signin offline_access';

export interface DeviceCode {
  device_code: string;
  user_code: string;
  verification_uri: string;
  expires_in: number;
  interval: number;
}

export interface MsaTokens {
  accessToken: string;
  refreshToken: string;
}

export interface MinecraftSession {
  accessToken: string;
  expiresAt: number;
  uuid: string;
  name: string;
  xuid: string;
}

export class AuthError extends Error {}

async function call(url: string, init: RequestInit): Promise<{ status: number; body: Record<string, unknown> }> {
  const response = await fetch(url, { ...init, headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', ...(init.headers ?? {}) } });
  const text = await response.text();
  let body: Record<string, unknown> = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  return { status: response.status, body };
}

function form(data: Record<string, string>): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(data).toString()
  };
}

function json(data: unknown, token?: string): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(data)
  };
}

export async function requestDeviceCode(clientId: string): Promise<DeviceCode> {
  const { status, body } = await call(`${MSA}/devicecode`, form({ client_id: clientId, scope: SCOPE }));
  if (status !== 200) {
    throw new AuthError(`Microsoft login unavailable (${String(body.error_description ?? body.error ?? status)})`);
  }
  return body as unknown as DeviceCode;
}

/** Polls the token endpoint until the user finished signing in, the code expired or `signal` aborted. */
export async function pollDeviceCode(clientId: string, code: DeviceCode, signal: AbortSignal): Promise<MsaTokens> {
  let interval = Math.max(1, code.interval) * 1000;
  const deadline = Date.now() + code.expires_in * 1000;
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, interval));
    if (signal.aborted) {
      throw new AuthError('Login cancelled');
    }
    const { status, body } = await call(`${MSA}/token`, form({
      grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
      client_id: clientId,
      device_code: code.device_code
    }));
    if (status === 200) {
      return { accessToken: String(body.access_token), refreshToken: String(body.refresh_token) };
    }
    if (body.error === 'authorization_pending') {
      continue;
    }
    if (body.error === 'slow_down') {
      interval += 5000;
      continue;
    }
    throw new AuthError(body.error === 'expired_token' ? 'The login code expired. Try again.' : `Login failed (${String(body.error_description ?? body.error)})`);
  }
  throw new AuthError('The login code expired. Try again.');
}

export async function refreshMsa(clientId: string, refreshToken: string): Promise<MsaTokens> {
  const { status, body } = await call(`${MSA}/token`, form({
    grant_type: 'refresh_token',
    client_id: clientId,
    refresh_token: refreshToken,
    scope: SCOPE
  }));
  if (status !== 200) {
    throw new AuthError('Your Microsoft session expired. Please sign in again.');
  }
  return { accessToken: String(body.access_token), refreshToken: String(body.refresh_token ?? refreshToken) };
}

const XSTS_ERRORS: Record<string, string> = {
  '2148916233': 'This Microsoft account has no Xbox profile. Create one at xbox.com and try again.',
  '2148916235': 'Xbox Live is not available in your country.',
  '2148916236': 'This account needs adult verification (South Korea).',
  '2148916237': 'This account needs adult verification (South Korea).',
  '2148916238': 'Child accounts must be added to a Microsoft family by an adult.'
};

/** Exchanges an MSA access token for a Minecraft session and verifies game ownership. */
export async function loginMinecraft(msaAccessToken: string): Promise<MinecraftSession> {
  const xbl = await call('https://user.auth.xboxlive.com/user/authenticate', json({
    Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${msaAccessToken}` },
    RelyingParty: 'http://auth.xboxlive.com',
    TokenType: 'JWT'
  }));
  if (xbl.status !== 200) {
    throw new AuthError('Xbox Live authentication failed.');
  }
  const xblToken = String(xbl.body.Token);
  const uhs = (xbl.body.DisplayClaims as { xui: { uhs: string }[] }).xui[0].uhs;

  const xsts = await call('https://xsts.auth.xboxlive.com/xsts/authorize', json({
    Properties: { SandboxId: 'RETAIL', UserTokens: [xblToken] },
    RelyingParty: 'rp://api.minecraftservices.com/',
    TokenType: 'JWT'
  }));
  if (xsts.status !== 200) {
    throw new AuthError(XSTS_ERRORS[String(xsts.body.XErr)] ?? 'Xbox authorisation failed.');
  }
  const xstsToken = String(xsts.body.Token);
  const xuid = (xsts.body.DisplayClaims as { xui: { xid?: string }[] })?.xui?.[0]?.xid ?? '';

  const mc = await call('https://api.minecraftservices.com/authentication/login_with_xbox', json({
    identityToken: `XBL3.0 x=${uhs};${xstsToken}`
  }));
  if (mc.status !== 200) {
    throw new AuthError(mc.status === 403
      ? 'Minecraft login was rejected for this launcher application id (Mojang approval required).'
      : 'Minecraft services login failed.');
  }
  const accessToken = String(mc.body.access_token);
  const expiresAt = Date.now() + Number(mc.body.expires_in ?? 86_400) * 1000;

  const entitlements = await call('https://api.minecraftservices.com/entitlements/mcstore', {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  const items = (entitlements.body.items as unknown[] | undefined) ?? [];
  if (items.length === 0) {
    throw new AuthError('This Microsoft account does not own Minecraft: Java Edition.');
  }
  const profile = await call('https://api.minecraftservices.com/minecraft/profile', {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (profile.status !== 200) {
    throw new AuthError('This account has no Minecraft profile yet. Start Minecraft once with the official launcher to pick a name.');
  }
  return { accessToken, expiresAt, uuid: String(profile.body.id), name: String(profile.body.name), xuid };
}
