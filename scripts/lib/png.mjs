// Minimal PNG codec for the visual tests: 8-bit greyscale/RGB/RGBA (with or without alpha),
// non-interlaced — what Minecraft's screenshots and our diff images use. No dependencies.
import { deflateSync, inflateSync } from 'node:zlib';

const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const CHANNELS = { 0: 1, 2: 3, 4: 2, 6: 4 };

const CRC_TABLE = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(bytes) {
  let c = 0xffffffff;
  for (const b of bytes) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

/** Decodes a PNG into { width, height, data } with data as RGBA bytes. */
export function decodePng(file) {
  if (!file.subarray(0, 8).equals(SIGNATURE)) throw new Error('not a PNG file');
  let width = 0;
  let height = 0;
  let channels = 0;
  const idat = [];
  for (let at = 8; at < file.length; ) {
    const length = file.readUInt32BE(at);
    const type = file.toString('latin1', at + 4, at + 8);
    const body = file.subarray(at + 8, at + 8 + length);
    if (type === 'IHDR') {
      width = body.readUInt32BE(0);
      height = body.readUInt32BE(4);
      const depth = body[8];
      const colorType = body[9];
      if (depth !== 8 || !(colorType in CHANNELS) || body[12] !== 0) {
        throw new Error(`unsupported PNG (bit depth ${depth}, colour type ${colorType}, interlace ${body[12]})`);
      }
      channels = CHANNELS[colorType];
    } else if (type === 'IDAT') {
      idat.push(body);
    } else if (type === 'IEND') {
      break;
    }
    at += 12 + length;
  }
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const pixels = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    const out = pixels.subarray(y * stride, (y + 1) * stride);
    const prev = y > 0 ? pixels.subarray((y - 1) * stride, y * stride) : null;
    for (let i = 0; i < stride; i++) {
      const a = i >= channels ? out[i - channels] : 0;
      const b = prev ? prev[i] : 0;
      const c = prev && i >= channels ? prev[i - channels] : 0;
      let value = line[i];
      switch (filter) {
        case 0: break;
        case 1: value += a; break;
        case 2: value += b; break;
        case 3: value += (a + b) >> 1; break;
        case 4: {
          const p = a + b - c;
          const pa = Math.abs(p - a);
          const pb = Math.abs(p - b);
          const pc = Math.abs(p - c);
          value += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
          break;
        }
        default: throw new Error(`bad PNG filter ${filter}`);
      }
      out[i] = value & 0xff;
    }
  }
  const data = Buffer.alloc(width * height * 4);
  for (let p = 0; p < width * height; p++) {
    const s = p * channels;
    const grey = channels <= 2;
    data[p * 4] = pixels[s];
    data[p * 4 + 1] = grey ? pixels[s] : pixels[s + 1];
    data[p * 4 + 2] = grey ? pixels[s] : pixels[s + 2];
    data[p * 4 + 3] = channels === 4 ? pixels[s + 3] : channels === 2 ? pixels[s + 1] : 255;
  }
  return { width, height, data };
}

function chunk(type, body) {
  const head = Buffer.alloc(8);
  head.writeUInt32BE(body.length, 0);
  head.write(type, 4, 'latin1');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), body])), 0);
  return Buffer.concat([head, body, crc]);
}

/** Encodes RGBA bytes as a PNG (no filtering — small images, simplicity over size). */
export function encodePng({ width, height, data }) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6;
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    data.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  return Buffer.concat([SIGNATURE, chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

/**
 * Compares two images. A pixel differs when any channel differs by more than {@code threshold}.
 * Returns the count, the ratio and a diff image: the expected image dimmed, differences in red.
 */
export function compareImages(expected, actual, threshold = 32) {
  if (expected.width !== actual.width || expected.height !== actual.height) {
    return { sizeMismatch: true, differing: expected.width * expected.height, ratio: 1, diff: null };
  }
  const { width, height } = expected;
  const diff = Buffer.alloc(width * height * 4);
  let differing = 0;
  for (let p = 0; p < width * height; p++) {
    const i = p * 4;
    let delta = 0;
    for (let c = 0; c < 4; c++) delta = Math.max(delta, Math.abs(expected.data[i + c] - actual.data[i + c]));
    if (delta > threshold) {
      differing++;
      diff[i] = 255;
      diff[i + 1] = 0;
      diff[i + 2] = 0;
    } else {
      const grey = (expected.data[i] + expected.data[i + 1] + expected.data[i + 2]) / 3;
      diff[i] = diff[i + 1] = diff[i + 2] = Math.round(grey * 0.3);
    }
    diff[i + 3] = 255;
  }
  return { sizeMismatch: false, differing, ratio: differing / (width * height), diff: { width, height, data: diff } };
}
