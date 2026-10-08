// File-backed storage: one JSON document per player (data/users/<uuid>.json), one per profile share
// code (data/shares/<code>.json) and grants.json.
// Small and dependency-free; every write is atomic (temporary file + rename).
import { mkdirSync, readFileSync, renameSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const UUID = /^[0-9a-f]{32}$/;
const CODE = /^MDN-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$/;

export class Store {
  constructor(dir) {
    this.dir = dir;
    this.users = new Map();
    mkdirSync(join(dir, 'users'), { recursive: true });
    mkdirSync(join(dir, 'shares'), { recursive: true });
  }

  /** A profile share ({ code, profile, createdAt, expiresAt, deleteKey }) or null. */
  share(code) {
    const file = CODE.test(code) ? join(this.dir, 'shares', `${code}.json`) : null;
    return file && existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null;
  }

  saveShare(share) {
    if (!CODE.test(share.code)) {
      throw new Error(`bad share code ${share.code}`);
    }
    atomicWrite(join(this.dir, 'shares', `${share.code}.json`), JSON.stringify(share));
  }

  deleteShare(code) {
    if (CODE.test(code)) {
      rmSync(join(this.dir, 'shares', `${code}.json`), { force: true });
    }
  }

  /** The player's document, created on first use. {@code uuid} is 32 lowercase hex digits. */
  user(uuid) {
    if (!UUID.test(uuid)) {
      throw new Error(`bad uuid ${uuid}`);
    }
    let user = this.users.get(uuid);
    if (!user) {
      const file = join(this.dir, 'users', `${uuid}.json`);
      user = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : { uuid, name: null, loadout: {}, profiles: {} };
      this.users.set(uuid, user);
    }
    return user;
  }

  /** The document if the player was ever stored, without creating one. */
  existing(uuid) {
    if (!UUID.test(uuid)) {
      return null;
    }
    if (this.users.has(uuid)) {
      return this.users.get(uuid);
    }
    return existsSync(join(this.dir, 'users', `${uuid}.json`)) ? this.user(uuid) : null;
  }

  save(user) {
    this.users.set(user.uuid, user);
    atomicWrite(join(this.dir, 'users', `${user.uuid}.json`), JSON.stringify(user));
  }

  /** Cosmetic id → player uuids allowed to use it (for cosmetics with access "grant"). */
  grants() {
    const file = join(this.dir, 'grants.json');
    return existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : {};
  }
}

function atomicWrite(file, text) {
  const temporary = `${file}.${process.pid}.tmp`;
  writeFileSync(temporary, text);
  renameSync(temporary, file);
}
