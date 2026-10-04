import assert from 'node:assert/strict';
import { test } from 'node:test';
import { deflateSync } from 'node:zlib';
import { compareImages, decodePng, encodePng } from '../lib/png.mjs';

function gradientImage(width, height) {
  const data = Buffer.alloc(width * height * 4);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      data[i] = x * 16;
      data[i + 1] = y * 16;
      data[i + 2] = (x + y) * 8;
      data[i + 3] = 255 - x;
    }
  }
  return { width, height, data };
}

test('encoded images decode to the same pixels', () => {
  const image = gradientImage(13, 7);
  const decoded = decodePng(encodePng(image));
  assert.equal(decoded.width, 13);
  assert.equal(decoded.height, 7);
  assert.deepEqual(decoded.data, image.data);
});

test('every PNG filter type is undone (RGB input)', () => {
  // 3×2 RGB image, one row per filter: Sub, Up, Average, Paeth
  const width = 3;
  const rows = [
    [10, 20, 30, 40, 50, 60, 70, 80, 90],
    [15, 25, 35, 45, 55, 65, 75, 85, 95],
    [200, 100, 50, 210, 110, 60, 220, 120, 70],
    [5, 6, 7, 8, 9, 10, 11, 12, 13],
  ];
  const filtered = [];
  const filters = [1, 2, 3, 4];
  rows.forEach((row, y) => {
    const prev = y > 0 ? rows[y - 1] : row.map(() => 0);
    const out = [filters[y]];
    row.forEach((value, i) => {
      const a = i >= 3 ? row[i - 3] : 0;
      const b = prev[i];
      const c = i >= 3 ? prev[i - 3] : 0;
      let predictor;
      if (filters[y] === 1) predictor = a;
      else if (filters[y] === 2) predictor = b;
      else if (filters[y] === 3) predictor = (a + b) >> 1;
      else {
        const p = a + b - c;
        const pa = Math.abs(p - a);
        const pb = Math.abs(p - b);
        const pc = Math.abs(p - c);
        predictor = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      out.push((value - predictor) & 0xff);
    });
    filtered.push(...out);
  });
  // a hand-built PNG: IHDR (colour type 2) + one IDAT, CRCs are not checked by the decoder
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(rows.length, 4);
  header[8] = 8;
  header[9] = 2;
  const chunk = (type, body) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(body.length);
    return Buffer.concat([length, Buffer.from(type, 'latin1'), body, Buffer.alloc(4)]);
  };
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.from(filtered))),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  const decoded = decodePng(png);
  rows.forEach((row, y) => {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      assert.deepEqual([...decoded.data.subarray(i, i + 4)], [row[x * 3], row[x * 3 + 1], row[x * 3 + 2], 255]);
    }
  });
});

test('comparison counts pixels beyond the threshold', () => {
  const expected = gradientImage(10, 10);
  const actual = { ...expected, data: Buffer.from(expected.data) };
  actual.data[0] += 10; // within the threshold
  actual.data[4 * 5 + 1] += 100; // differs
  const result = compareImages(expected, actual, 32);
  assert.equal(result.differing, 1);
  assert.equal(result.ratio, 0.01);
  assert.deepEqual([...result.diff.data.subarray(20, 24)], [255, 0, 0, 255]);
  assert.equal(compareImages(expected, gradientImage(10, 9)).sizeMismatch, true);
});
