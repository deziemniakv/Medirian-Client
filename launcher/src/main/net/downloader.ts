import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { copyFile, mkdir, rename, rm, stat } from 'node:fs/promises';
import { dirname } from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { request } from './http';

export interface DownloadJob {
  url: string;
  path: string;
  sha1?: string;
  size?: number;
  /** Optional local file to copy from instead of downloading (e.g. existing .minecraft assets). */
  localSource?: string;
}

export type VerifyMode = 'quick' | 'full';

export interface DownloadProgress {
  done: number;
  total: number;
  bytesDone: number;
  bytesTotal: number;
}

export interface DownloadOptions {
  concurrency: number;
  /** quick: existing files are accepted when the size matches; full: SHA-1 is always verified. */
  verify: VerifyMode;
  onProgress?: (progress: DownloadProgress) => void;
}

export interface DownloadResult {
  checked: number;
  downloaded: number;
}

export async function sha1File(path: string): Promise<string> {
  const hash = createHash('sha1');
  await pipeline(createReadStream(path), hash);
  return hash.digest('hex');
}

/** Whether `path` exists and matches the expected size/hash for the given verify mode. */
export async function isValid(path: string, sha1: string | undefined, size: number | undefined, mode: VerifyMode): Promise<boolean> {
  let info;
  try {
    info = await stat(path);
  } catch {
    return false;
  }
  if (!info.isFile()) {
    return false;
  }
  if (size !== undefined && info.size !== size) {
    return false;
  }
  if (mode === 'full' && sha1) {
    return (await sha1File(path)) === sha1.toLowerCase();
  }
  return size !== undefined || !sha1 || mode === 'quick';
}

/**
 * Downloads (or copies) every job that is missing or invalid, with bounded concurrency,
 * SHA-1 verification and atomic writes (`.part` file renamed on success).
 */
export async function downloadAll(jobs: DownloadJob[], options: DownloadOptions): Promise<DownloadResult> {
  const unique = dedupe(jobs);
  const progress: DownloadProgress = {
    done: 0,
    total: unique.length,
    bytesDone: 0,
    bytesTotal: unique.reduce((sum, job) => sum + (job.size ?? 0), 0)
  };
  let downloaded = 0;
  let index = 0;
  let lastEmit = 0;
  const emit = (force = false) => {
    const now = Date.now();
    if (options.onProgress && (force || now - lastEmit > 100)) {
      lastEmit = now;
      options.onProgress({ ...progress });
    }
  };

  const worker = async () => {
    while (index < unique.length) {
      const job = unique[index++];
      if (!(await isValid(job.path, job.sha1, job.size, options.verify))) {
        await fetchOne(job, (bytes) => {
          progress.bytesDone += bytes;
          emit();
        });
        downloaded++;
      } else {
        progress.bytesDone += job.size ?? 0;
      }
      progress.done++;
      emit();
    }
  };
  const workers = Array.from({ length: Math.max(1, Math.min(options.concurrency, unique.length)) }, worker);
  await Promise.all(workers);
  emit(true);
  return { checked: unique.length, downloaded };
}

function dedupe(jobs: DownloadJob[]): DownloadJob[] {
  const seen = new Map<string, DownloadJob>();
  for (const job of jobs) {
    if (!seen.has(job.path)) {
      seen.set(job.path, job);
    }
  }
  return [...seen.values()];
}

async function fetchOne(job: DownloadJob, onBytes: (bytes: number) => void, attempt = 0): Promise<void> {
  await mkdir(dirname(job.path), { recursive: true });
  const part = `${job.path}.part`;
  try {
    if (job.localSource && (await isValid(job.localSource, job.sha1, job.size, 'quick'))) {
      await copyFile(job.localSource, part);
      onBytes(job.size ?? 0);
    } else {
      const response = await request(job.url, { timeoutMs: 60_000, retries: 2 });
      if (!response.body) {
        throw new Error(`Empty response for ${job.url}`);
      }
      const counter = new Transform({
        transform(chunk: Buffer, _encoding, callback) {
          onBytes(chunk.length);
          callback(null, chunk);
        }
      });
      await pipeline(Readable.fromWeb(response.body as never), counter, createWriteStream(part));
    }
    if (job.sha1) {
      const actual = await sha1File(part);
      if (actual !== job.sha1.toLowerCase()) {
        throw new Error(`Checksum mismatch for ${job.url} (expected ${job.sha1}, got ${actual})`);
      }
    }
    await rename(part, job.path);
  } catch (error) {
    await rm(part, { force: true });
    if (attempt < 2) {
      await new Promise((resolve) => setTimeout(resolve, 750 * (attempt + 1)));
      return fetchOne({ ...job, localSource: undefined }, onBytes, attempt + 1);
    }
    throw error;
  }
}
