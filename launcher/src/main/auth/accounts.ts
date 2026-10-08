import { createHash } from 'node:crypto';
import { safeStorage } from 'electron';
import type { Account, DeviceCodeInfo, LoginResult } from '../../common/types';
import { readJson, writeJson } from '../core/json';
import { log } from '../core/log';
import { AuthError, loginMinecraft, pollDeviceCode, refreshMsa, requestDeviceCode } from './microsoft';

interface StoredAccount {
  uuid: string;
  name: string;
  type: 'microsoft' | 'offline';
  /** encrypted with safeStorage, base64 */
  refreshToken?: string;
  minecraftToken?: string;
  minecraftTokenExpiresAt?: number;
  xuid?: string;
}

interface AccountsFile {
  version: 1;
  current: string | null;
  accounts: StoredAccount[];
}

/** Everything the game needs to start as a user. Never sent to the renderer. */
export interface LaunchSession {
  name: string;
  uuid: string;
  accessToken: string;
  xuid: string;
  userType: 'msa' | 'legacy';
  clientId: string;
}

/** Offline UUID exactly as vanilla computes it: UUID v3 of "OfflinePlayer:<name>". */
export function offlineUuid(name: string): string {
  const hash = createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  return hash.toString('hex');
}

/**
 * Stores the signed-in account. Tokens are encrypted with Electron's safeStorage (DPAPI on
 * Windows, Keychain on macOS, libsecret on Linux) and never leave the main process.
 */
export class AccountService {
  private data: AccountsFile = { version: 1, current: null, accounts: [] };
  private login: AbortController | null = null;

  constructor(
    private readonly file: string,
    private readonly clientId: () => string,
    private readonly offlineAllowed: boolean,
    private readonly emit: (account: Account | null) => void,
    private readonly emitLogin: (result: LoginResult) => void
  ) {}

  async load(): Promise<void> {
    this.data = await readJson<AccountsFile>(this.file, this.data);
    if (!this.offlineAllowed) {
      // offline accounts only exist in development builds
      this.data.accounts = this.data.accounts.filter((a) => a.type !== 'offline');
      if (!this.data.accounts.some((a) => a.uuid === this.data.current)) {
        this.data.current = this.data.accounts[0]?.uuid ?? null;
      }
    }
  }

  isOfflineAllowed(): boolean {
    return this.offlineAllowed;
  }

  current(): Account | null {
    const stored = this.stored();
    return stored ? { uuid: stored.uuid, name: stored.name, type: stored.type } : null;
  }

  private stored(): StoredAccount | undefined {
    return this.data.accounts.find((a) => a.uuid === this.data.current);
  }

  async startLogin(): Promise<DeviceCodeInfo> {
    const clientId = this.clientId();
    if (!clientId) {
      throw new AuthError('Microsoft login is not available in this build (MEDIRIAN_MSA_CLIENT_ID, docs/OWNER_SETUP.md).');
    }
    this.login?.abort();
    const controller = new AbortController();
    this.login = controller;
    const code = await requestDeviceCode(clientId);
    void (async () => {
      try {
        const tokens = await pollDeviceCode(clientId, code, controller.signal);
        const session = await loginMinecraft(tokens.accessToken);
        await this.upsert({
          uuid: session.uuid,
          name: session.name,
          type: 'microsoft',
          refreshToken: this.encrypt(tokens.refreshToken),
          minecraftToken: this.encrypt(session.accessToken),
          minecraftTokenExpiresAt: session.expiresAt,
          xuid: session.xuid
        });
        log.info(`Signed in as ${session.name}`);
        this.emitLogin({ ok: true, account: this.current()! });
      } catch (error) {
        if (!controller.signal.aborted) {
          log.warn('Microsoft login failed', error);
          this.emitLogin({ ok: false, error: error instanceof Error ? error.message : String(error) });
        }
      } finally {
        if (this.login === controller) {
          this.login = null;
        }
      }
    })();
    return { userCode: code.user_code, verificationUri: code.verification_uri, expiresInSec: code.expires_in };
  }

  cancelLogin(): void {
    this.login?.abort();
    this.login = null;
  }

  async logout(): Promise<void> {
    const current = this.data.current;
    this.data.accounts = this.data.accounts.filter((a) => a.uuid !== current);
    this.data.current = this.data.accounts[0]?.uuid ?? null;
    await this.persist();
    this.emit(this.current());
  }

  /** Development only: an offline account for testing the launch pipeline in singleplayer. */
  async offline(name: string): Promise<Account> {
    if (!this.offlineAllowed) {
      throw new Error('Offline accounts are only available in development builds.');
    }
    const clean = name.trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(clean)) {
      throw new Error('Use 3–16 letters, digits or underscores.');
    }
    await this.upsert({ uuid: offlineUuid(clean), name: clean, type: 'offline' });
    return this.current()!;
  }

  /** Returns a valid session for launching, refreshing tokens when needed. */
  async session(): Promise<LaunchSession> {
    const account = this.stored();
    if (!account) {
      throw new Error('Sign in to play.');
    }
    if (account.type === 'offline') {
      return { name: account.name, uuid: account.uuid, accessToken: '0', xuid: '', userType: 'legacy', clientId: '' };
    }
    let token = account.minecraftToken ? this.decrypt(account.minecraftToken) : '';
    if (!token || !account.minecraftTokenExpiresAt || account.minecraftTokenExpiresAt - Date.now() < 10 * 60_000) {
      if (!account.refreshToken) {
        throw new Error('Your session expired. Please sign in again.');
      }
      const msa = await refreshMsa(this.clientId(), this.decrypt(account.refreshToken));
      const session = await loginMinecraft(msa.accessToken);
      account.refreshToken = this.encrypt(msa.refreshToken);
      account.minecraftToken = this.encrypt(session.accessToken);
      account.minecraftTokenExpiresAt = session.expiresAt;
      account.name = session.name;
      account.xuid = session.xuid;
      await this.persist();
      token = session.accessToken;
    }
    return { name: account.name, uuid: account.uuid, accessToken: token, xuid: account.xuid ?? '', userType: 'msa', clientId: this.clientId() };
  }

  private async upsert(account: StoredAccount): Promise<void> {
    this.data.accounts = this.data.accounts.filter((a) => a.uuid !== account.uuid);
    this.data.accounts.push(account);
    this.data.current = account.uuid;
    await this.persist();
    this.emit(this.current());
  }

  private persist(): Promise<void> {
    return writeJson(this.file, this.data);
  }

  private encrypt(value: string): string {
    if (!safeStorage.isEncryptionAvailable()) {
      throw new Error('Secure storage is not available on this system; cannot store login tokens.');
    }
    return safeStorage.encryptString(value).toString('base64');
  }

  private decrypt(value: string): string {
    return safeStorage.decryptString(Buffer.from(value, 'base64'));
  }
}
