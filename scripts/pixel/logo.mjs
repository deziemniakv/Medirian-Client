// Medirian Client logo in pixel art: the "M" mark from branding/source/MedirianClient.png,
// pixelated and shaded, and the MEDIRIAN CLIENT wordmark lettered with the Medirian Pixel font.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { decodePng } from '../lib/png.mjs';
import { Canvas } from './canvas.mjs';
import { layout, textWidth } from './font.mjs';
import { PAL } from './palette.mjs';

const MARK_COLORS = {
  // [base, top highlight, bottom/right shade] for the two halves of the mark
  left: ['#6a2e96', '#8c4cbd', '#4b1d6c'],
  right: ['#a267d6', '#c99cf0', '#7c42b0']
};

/** Pixelates the source mark to {@code width} pixels; each pixel is 'L' (left half), 'R' or null. */
function pixelateMark(root, width) {
  const src = decodePng(readFileSync(join(root, 'branding/source/MedirianClient.png')));
  const at = (x, y) => {
    const i = (y * src.width + x) * 4;
    return [src.data[i], src.data[i + 1], src.data[i + 2], src.data[i + 3]];
  };
  const ink = ([r, g, b, a]) => a >= 128 && r + g + b < 600;
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = 0; y < src.height; y++) {
    for (let x = 0; x < src.width; x++) {
      if (ink(at(x, y))) {
        x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
      }
    }
  }
  const sw = x1 - x0 + 1;
  const sh = y1 - y0 + 1;
  const height = Math.round((width * sh) / sw);
  const grid = [];
  for (let j = 0; j < height; j++) {
    const row = [];
    for (let i = 0; i < width; i++) {
      let dark = 0, light = 0, n = 0;
      for (let y = Math.floor(y0 + (j / height) * sh); y < y0 + ((j + 1) / height) * sh; y++) {
        for (let x = Math.floor(x0 + (i / width) * sw); x < x0 + ((i + 1) / width) * sw; x++) {
          n++;
          const p = at(x, y);
          if (!ink(p)) continue;
          if (p[0] < 70) dark++;
          else light++;
        }
      }
      row.push((dark + light) / n > 0.42 ? (dark >= light ? 'L' : 'R') : null);
    }
    grid.push(row);
  }
  return grid;
}

/** The shaded mark, {@code width} pixels wide, with a dark outline unless {@code outline} is false. */
export function mark(root, width, { outline = true } = {}) {
  const grid = pixelateMark(root, width);
  const pad = outline ? 1 : 0;
  const c = new Canvas(width + pad * 2, grid.length + pad * 2);
  const has = (i, j) => j >= 0 && j < grid.length && i >= 0 && i < width && grid[j][i] !== null;
  grid.forEach((row, j) => row.forEach((cell, i) => {
    if (!cell) return;
    const [base, light, dark] = cell === 'L' ? MARK_COLORS.left : MARK_COLORS.right;
    let color = base;
    if (!has(i, j - 1)) color = light;
    else if (!has(i, j + 1) || (cell === 'R' && !has(i + 1, j))) color = dark;
    c.set(i + pad, j + pad, color);
  }));
  if (outline) c.outline(PAL.ink, true);
  return c;
}

/**
 * Chunky lettering: every font pixel becomes {@code scale}×{@code scale}, lit from the top
 * ({@code top} colour on the upper half of the cap height, {@code bottom} below), with a dark
 * extrusion of {@code depth} pixels and an outline.
 */
export function chunkyText(text, { scale = 2, top, bottom, side, depth = 2, outline = PAL.ink }) {
  const w = textWidth(text) * scale;
  const h = 7 * scale;
  const pad = 2 * scale;
  const c = new Canvas(w + pad * 2 + depth, h + pad * 2 + depth);
  const pixels = [];
  layout(text, (x, r) => pixels.push([x, r]));
  // extrusion first, then the face over it
  for (let d = depth; d >= 1; d--) {
    for (const [x, r] of pixels) c.rect(pad + x * scale + d, pad + r * scale + d, scale, scale, side);
  }
  for (const [x, r] of pixels) {
    for (let sy = 0; sy < scale; sy++) {
      const y = r * scale + sy;
      c.rect(pad + x * scale, pad + y, scale, 1, y < h * 0.5 ? top : bottom);
    }
  }
  c.outline(outline, false);
  return c;
}

/** Plain lettering in one colour, optional outline. */
export function text(str, color, { outline = null, scale = 1 } = {}) {
  const pad = outline ? 1 : 0;
  const c = new Canvas(textWidth(str) * scale + pad * 2, 9 * scale + pad * 2 + 2);
  layout(str, (x, r) => c.rect(pad + x * scale, pad + (r + 2) * scale, scale, scale, color));
  if (outline) c.outline(outline, true);
  return c;
}

/** Full lockup: mark on the left, MEDIRIAN over CLIENT on the right. */
export function lockup(root) {
  const m = mark(root, 30);
  const word = chunkyText('MEDIRIAN', { scale: 2, top: PAL.lavender, bottom: PAL.lilac, side: PAL.purple, depth: 2 });
  const client = text('CLIENT', PAL.pumpkinLight, { outline: PAL.ink, scale: 1 });
  // letter-space CLIENT to the wordmark's width
  const spaced = new Canvas(word.width - 6, client.height);
  const letters = [...'CLIENT'];
  const widths = letters.map((ch) => textWidth(ch));
  const total = widths.reduce((s, w) => s + w, 0);
  const gap = (spaced.width - 2 - total) / (letters.length - 1);
  let x = 1;
  letters.forEach((ch, i) => {
    layout(ch, (gx, r) => spaced.set(Math.round(x) + gx, 1 + r + 2, PAL.pumpkinLight));
    x += widths[i] + gap;
  });
  spaced.outline(PAL.ink, true);
  const width = m.width + 6 + word.width;
  const height = Math.max(m.height, word.height + client.height - 3);
  const c = new Canvas(width, height);
  c.draw(m, 0, Math.round((height - m.height) / 2));
  c.draw(word, m.width + 6, 0);
  c.draw(spaced, m.width + 6 + 3, word.height - 4);
  return c;
}
