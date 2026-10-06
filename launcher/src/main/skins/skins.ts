import { existsSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { Account, PlayerSkin } from '../../common/types';
import { readJson, writeJson } from '../core/json';
import { log } from '../core/log';
import { HttpError, request } from '../net/http';

const SESSION = 'https://sessionserver.mojang.com/session/minecraft/profile/';
const NAME_LOOKUP = 'https://api.mojang.com/users/profiles/minecraft/';
/** A refresh is skipped when the skin was fetched less than this long ago (unless forced). */
const FRESH_MS = 2 * 60_000;

interface CachedSkin {
  url: string | null;
  model: 'classic' | 'slim';
  name: string;
  fetchedAt: number;
}

interface TexturesPayload {
  textures?: { SKIN?: { url: string; metadata?: { model?: string } } };
}

/** The skin URL and model from a session profile's "textures" property. */
export function parseTextures(properties: { name: string; value: string }[]): { url: string | null; model: 'classic' | 'slim' } {
  const property = properties.find((p) => p.name === 'textures');
  if (!property) {
    return { url: null, model: 'classic' };
  }
  const payload = JSON.parse(Buffer.from(property.value, 'base64').toString('utf8')) as TexturesPayload;
  const skin = payload.textures?.SKIN;
  return {
    // Mojang still hands out http:// links; the same files are served over https
    url: skin?.url ? skin.url.replace(/^http:\/\//, 'https://') : null,
    model: skin?.metadata?.model === 'slim' ? 'slim' : 'classic'
  };
}

/**
 * The signed-in player's Minecraft skin, as Mojang serves it (sessionserver profile → textures).
 * Cached in MEDIRIAN_HOME/cache/skins/<uuid>.png (+ .json) — the game's main menu reads the same
 * files — and refreshed when the account changes, when the launcher comes back to the front and
 * every 10 minutes, so a skin changed on minecraft.net shows up without restarting.
 */
export class SkinService {
  private readonly dir: string;
  private readonly account: () => Account | null;
  private readonly emit: (skin: PlayerSkin) => void;
  private current: PlayerSkin = { uuid: null, name: null, texture: null, model: 'classic', source: 'default', updatedAt: 0 };
  private refreshing: Promise<PlayerSkin> | null = null;

  constructor(cacheDir: string, account: () => Account | null, emit: (skin: PlayerSkin) => void) {
    this.dir = join(cacheDir, 'skins');
    this.account = account;
    this.emit = emit;
  }

  get(): PlayerSkin {
    return this.current;
  }

  /** Shows the cached skin of the current account right away (no network). */
  async loadCached(): Promise<PlayerSkin> {
    const account = this.account();
    if (!account) {
      return this.set(this.fallback(null));
    }
    const cached = await this.readCache(account.uuid);
    return this.set(cached ?? this.fallback(account));
  }

  /** Fetches the skin again (at most every 2 minutes unless forced) and emits it when it changed. */
  refresh(force = false): Promise<PlayerSkin> {
    const account = this.account();
    if (!account) {
      return Promise.resolve(this.set(this.fallback(null)));
    }
    const sameAccount = this.current.uuid === account.uuid;
    if (!force && sameAccount && this.current.source === 'mojang' && Date.now() - this.current.updatedAt < FRESH_MS) {
      return Promise.resolve(this.current);
    }
    this.refreshing ??= this.fetch(account).finally(() => {
      this.refreshing = null;
    });
    return this.refreshing;
  }

  private async fetch(account: Account): Promise<PlayerSkin> {
    try {
      // offline (development) accounts have no Mojang profile: use the player's profile of that name, if any
      let uuid: string | null = account.uuid.replace(/-/g, '');
      if (account.type === 'offline') {
        uuid = await this.lookupName(account.name);
      }
      const { url, model } = uuid ? await this.textures(uuid) : { url: null, model: 'classic' as const };
      if (!url) {
        await this.writeCache(account.uuid, { url: null, model, name: account.name, fetchedAt: Date.now() }, null);
        return this.set(this.fallback(account));
      }
      const cachedMeta = await readJson<CachedSkin | null>(join(this.dir, `${account.uuid}.json`), null);
      let png: Buffer | null = null;
      if (cachedMeta?.url === url && existsSync(join(this.dir, `${account.uuid}.png`))) {
        png = await readFile(join(this.dir, `${account.uuid}.png`));
      } else {
        const response = await request(url, { timeoutMs: 15_000, retries: 1 });
        png = Buffer.from(await response.arrayBuffer());
        log.info(`Skin of ${account.name} updated (${model})`);
      }
      await this.writeCache(account.uuid, { url, model, name: account.name, fetchedAt: Date.now() }, png);
      return this.set({ uuid: account.uuid, name: account.name, texture: this.dataUrl(png), model, source: 'mojang', updatedAt: Date.now() });
    } catch (error) {
      log.warn(`Could not fetch the skin of ${account.name}`, error);
      const cached = await this.readCache(account.uuid);
      const message = error instanceof Error ? error.message : String(error);
      return this.set({ ...(cached ?? this.fallback(account)), error: `Mojang's skin service did not answer (${message}).` });
    }
  }

  private async lookupName(name: string): Promise<string | null> {
    try {
      const response = await request(NAME_LOOKUP + encodeURIComponent(name), { timeoutMs: 10_000, retries: 0 });
      if (response.status === 204) {
        return null;
      }
      return ((await response.json()) as { id: string }).id;
    } catch (error) {
      if (error instanceof HttpError && error.status === 404) {
        return null;
      }
      throw error;
    }
  }

  private async textures(uuid: string): Promise<{ url: string | null; model: 'classic' | 'slim' }> {
    const response = await request(SESSION + uuid, { timeoutMs: 10_000, retries: 1 });
    if (response.status === 204) {
      return { url: null, model: 'classic' };
    }
    const profile = (await response.json()) as { properties?: { name: string; value: string }[] };
    return parseTextures(profile.properties ?? []);
  }

  private async readCache(uuid: string): Promise<PlayerSkin | null> {
    const meta = await readJson<CachedSkin | null>(join(this.dir, `${uuid}.json`), null);
    const png = join(this.dir, `${uuid}.png`);
    if (!meta) {
      return null;
    }
    if (!meta.url || !existsSync(png)) {
      return { ...this.fallback({ uuid, name: meta.name, type: 'microsoft' }), updatedAt: meta.fetchedAt };
    }
    return { uuid, name: meta.name, texture: this.dataUrl(await readFile(png)), model: meta.model, source: 'mojang', updatedAt: meta.fetchedAt };
  }

  private async writeCache(uuid: string, meta: CachedSkin, png: Buffer | null): Promise<void> {
    await mkdir(this.dir, { recursive: true });
    if (png) {
      await writeFile(join(this.dir, `${uuid}.png`), png);
    }
    await writeJson(join(this.dir, `${uuid}.json`), meta);
  }

  private fallback(account: Account | null): PlayerSkin {
    return { uuid: account?.uuid ?? null, name: account?.name ?? null, texture: null, model: 'classic', source: 'default', updatedAt: Date.now() };
  }

  private dataUrl(png: Buffer): string {
    return `data:image/png;base64,${png.toString('base64')}`;
  }

  private set(skin: PlayerSkin): PlayerSkin {
    const changed = skin.texture !== this.current.texture || skin.model !== this.current.model || skin.uuid !== this.current.uuid
      || skin.error !== this.current.error;
    this.current = skin;
    if (changed) {
      this.emit(skin);
    }
    return skin;
  }
}
