import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, normalize, sep } from 'node:path';
import { inflateRawSync } from 'node:zlib';

/**
 * Minimal ZIP extractor (store + deflate, no ZIP64) used for legacy native libraries. Native jars
 * are small, so the archive is read into memory.
 */
export async function extractZip(archive: string, destination: string, exclude: string[] = []): Promise<number> {
  const data = await readFile(archive);
  const eocd = findEndOfCentralDirectory(data);
  const entries = data.readUInt16LE(eocd + 10);
  let offset = data.readUInt32LE(eocd + 16);
  const root = normalize(destination) + sep;
  let extracted = 0;

  for (let i = 0; i < entries; i++) {
    if (data.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error(`Corrupt zip central directory in ${archive}`);
    }
    const method = data.readUInt16LE(offset + 10);
    const compressedSize = data.readUInt32LE(offset + 20);
    const nameLength = data.readUInt16LE(offset + 28);
    const extraLength = data.readUInt16LE(offset + 30);
    const commentLength = data.readUInt16LE(offset + 32);
    const localOffset = data.readUInt32LE(offset + 42);
    const name = data.toString('utf8', offset + 46, offset + 46 + nameLength);
    offset += 46 + nameLength + extraLength + commentLength;

    if (name.endsWith('/') || exclude.some((prefix) => name.startsWith(prefix))) {
      continue;
    }
    const target = normalize(join(destination, name));
    if (!target.startsWith(root)) {
      continue; // zip-slip protection
    }
    const localNameLength = data.readUInt16LE(localOffset + 26);
    const localExtraLength = data.readUInt16LE(localOffset + 28);
    const start = localOffset + 30 + localNameLength + localExtraLength;
    const raw = data.subarray(start, start + compressedSize);
    let content: Buffer;
    if (method === 0) {
      content = raw;
    } else if (method === 8) {
      content = inflateRawSync(raw);
    } else {
      throw new Error(`Unsupported zip compression ${method} in ${archive}`);
    }
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, content);
    extracted++;
  }
  return extracted;
}

function findEndOfCentralDirectory(data: Buffer): number {
  for (let i = data.length - 22; i >= Math.max(0, data.length - 65_557); i--) {
    if (data.readUInt32LE(i) === 0x06054b50) {
      return i;
    }
  }
  throw new Error('Not a zip archive');
}
