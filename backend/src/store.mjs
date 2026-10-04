// File-backed storage: one JSON document per player (data/users/<uuid>.json) plus grants.json.
// Small and dependency-free; every write is atomic (temporary file + rename).
import { mkdirSync, readFileSync, renameSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const UUID = /^[0-9a-f]{32}$/;

export class Store {
  constructor(dir) {
    this.dir = dir;
    this.users = new Map();
    mkdirSync(join(dir, 'users'), { recursive: true });
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
