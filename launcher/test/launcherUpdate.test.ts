import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { test } from 'node:test';
import type { LauncherUpdateStatus } from '../src/common/types.ts';
import { LauncherUpdateService, type Updater } from '../src/main/updates/launcherUpdate.ts';

class FakeUpdater extends EventEmitter implements Updater {
  autoDownload = false;
  autoInstallOnAppQuit = false;
  checks = 0;
  installed: unknown[] | null = null;
  result: 'none' | 'available' | 'error' = 'none';

  async checkForUpdates(): Promise<unknown> {
    this.checks++;
    this.emit('checking-for-update');
    if (this.result === 'error') {
      throw new Error('offline');
    }
    if (this.result === 'none') {
      this.emit('update-not-available', {});
    } else {
      this.emit('update-available', { version: '9.9.9' });
      this.emit('download-progress', { percent: 41.7 });
    }
    return null;
  }

  quitAndInstall(...args: unknown[]): void {
    this.installed = args;
  }
}

const quiet = { info() {}, warn() {} };

test('without an updater (development, local builds) nothing happens', async () => {
  const events: LauncherUpdateStatus[] = [];
  const service = new LauncherUpdateService(null, (s) => events.push(s), quiet);
  assert.equal(service.current().state, 'unsupported');
  service.start();
  assert.equal((await service.check()).state, 'unsupported');
  service.install();
  assert.deepEqual(events, []);
});

test('an available update is downloaded and installed on request', async () => {
  const updater = new FakeUpdater();
  updater.result = 'available';
  const events: LauncherUpdateStatus[] = [];
  const service = new LauncherUpdateService(updater, (s) => events.push(s), quiet);
  assert.equal(updater.autoDownload, true);
  assert.equal(updater.autoInstallOnAppQuit, true);
  await service.check();
  assert.deepEqual(service.current(), { state: 'downloading', version: '9.9.9', percent: 41 });
  // nothing to install yet, and no second check while downloading
  service.install();
  assert.equal(updater.installed, null);
  await service.check();
  assert.equal(updater.checks, 1);
  updater.emit('update-downloaded', { version: '9.9.9' });
  assert.deepEqual(service.current(), { state: 'ready', version: '9.9.9' });
  service.install();
  // silently, then the new version starts
  assert.deepEqual(updater.installed, [true, true]);
  assert.deepEqual(events.map((e) => e.state), ['checking', 'downloading', 'downloading', 'ready']);
});

test('up to date and failed checks are reported', async () => {
  const updater = new FakeUpdater();
  const service = new LauncherUpdateService(updater, () => {}, quiet);
  assert.equal((await service.check()).state, 'latest');
  updater.result = 'error';
  const failed = await service.check();
  assert.equal(failed.state, 'error');
  assert.equal(failed.error, 'offline');
  updater.emit('error', new Error('bad signature'));
  assert.equal(service.current().error, 'bad signature');
});
