// A tiny RGBA pixel canvas for Medirian's pixel art (no anti-aliasing: every pixel is placed on purpose).
import { encodePng } from '../lib/png.mjs';

/** '#rrggbb' → [r, g, b]. */
export const rgb = (hex) => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];

/** Linear mix of two '#rrggbb' colours, t in 0..1. */
export function mix(a, b, t) {
  const x = rgb(a);
  const y = rgb(b);
  return '#' + x.map((c, i) => Math.round(c + (y[i] - c) * t).toString(16).padStart(2, '0')).join('');
}

/** 4×4 ordered (Bayer) dither threshold for a pixel, 0..1. */
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
export const bayer = (x, y) => (BAYER[(y & 3) * 4 + (x & 3)] + 0.5) / 16;

/** Deterministic pseudo-random 0..1 from any number of integers. */
export function hash(...n) {
  let h = 2166136261;
  for (const v of n) {
    h ^= v | 0;
    h = Math.imul(h, 16777619);
    h ^= h >>> 13;
  }
  return ((h >>> 0) % 100000) / 100000;
}

export class Canvas {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.data = Buffer.alloc(width * height * 4);
  }

  /** Paints a pixel; alpha (0..1) blends over what is there. */
  set(x, y, color, alpha = 1) {
    x = Math.floor(x);
    y = Math.floor(y);
    if (x < 0 || y < 0 || x >= this.width || y >= this.height || alpha <= 0 || !color) return;
    const [r, g, b] = rgb(color);
    const i = (y * this.width + x) * 4;
    if (alpha >= 1) {
      this.data[i] = r;
      this.data[i + 1] = g;
      this.data[i + 2] = b;
      this.data[i + 3] = 255;
      return;
    }
    const old = this.data[i + 3] / 255;
    const out = alpha + old * (1 - alpha);
    const blend = (c, o) => Math.round((c * alpha + o * old * (1 - alpha)) / out);
    this.data[i] = blend(r, this.data[i]);
    this.data[i + 1] = blend(g, this.data[i + 1]);
    this.data[i + 2] = blend(b, this.data[i + 2]);
    this.data[i + 3] = Math.round(out * 255);
  }

  get(x, y) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return null;
    const i = (y * this.width + x) * 4;
    if (this.data[i + 3] === 0) return null;
    return '#' + [0, 1, 2].map((c) => this.data[i + c].toString(16).padStart(2, '0')).join('');
  }

  alphaAt(x, y) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return 0;
    return this.data[(y * this.width + x) * 4 + 3];
  }

  clear(x, y) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
    this.data.fill(0, (y * this.width + x) * 4, (y * this.width + x) * 4 + 4);
  }

  rect(x, y, w, h, color, alpha = 1) {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, color, alpha);
  }

  hline(x1, x2, y, color) {
    for (let x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) this.set(x, y, color);
  }

  vline(x, y1, y2, color) {
    for (let y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) this.set(x, y, color);
  }

  /** Bresenham line. */
  line(x0, y0, x1, y1, color) {
    const dx = Math.abs(x1 - x0);
    const dy = -Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1;
    const sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      this.set(x0, y0, color);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x0 += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y0 += sy;
      }
    }
  }

  /** Filled pixel circle (centre on a pixel). */
  disc(cx, cy, r, color, alpha = 1) {
    for (let y = -r; y <= r; y++) {
      for (let x = -r; x <= r; x++) {
        if (x * x + y * y <= r * r + r * 0.6) this.set(cx + x, cy + y, color, alpha);
      }
    }
  }

  /**
   * Draws a character map: one string per row, each character a key of {@code legend}
   * ('.' and ' ' are transparent).
   */
  sprite(rows, legend, x, y, { flip = false } = {}) {
    rows.forEach((row, j) => {
      [...row].forEach((ch, i) => {
        if (ch === '.' || ch === ' ') return;
        const color = legend[ch];
        if (color === undefined) throw new Error(`no colour for '${ch}'`);
        if (color) this.set(flip ? x + row.length - 1 - i : x + i, y + j, color);
      });
    });
  }

  /** Copies another canvas onto this one (alpha-blended). */
  draw(src, x, y) {
    for (let j = 0; j < src.height; j++) {
      for (let i = 0; i < src.width; i++) {
        const k = (j * src.width + i) * 4;
        const a = src.data[k + 3];
        if (a === 0) continue;
        const hex = '#' + [0, 1, 2].map((c) => src.data[k + c].toString(16).padStart(2, '0')).join('');
        this.set(x + i, y + j, hex, a / 255);
      }
    }
  }

  /** Adds a 1-pixel outline of {@code color} around every opaque pixel (4-neighbourhood, or 8 with corners). */
  outline(color, corners = false) {
    const solid = (x, y) => this.alphaAt(x, y) > 0;
    const marks = [];
    for (let y = 0; y < this.height; y++) {
      for (let x = 0; x < this.width; x++) {
        if (solid(x, y)) continue;
        const n = solid(x - 1, y) || solid(x + 1, y) || solid(x, y - 1) || solid(x, y + 1);
        const d = corners && (solid(x - 1, y - 1) || solid(x + 1, y - 1) || solid(x - 1, y + 1) || solid(x + 1, y + 1));
        if (n || d) marks.push([x, y]);
      }
    }
    for (const [x, y] of marks) this.set(x, y, color);
    return this;
  }

  /** Nearest-neighbour upscale. */
  scaled(factor) {
    const out = new Canvas(this.width * factor, this.height * factor);
    for (let y = 0; y < out.height; y++) {
      for (let x = 0; x < out.width; x++) {
        const s = (Math.floor(y / factor) * this.width + Math.floor(x / factor)) * 4;
        this.data.copy(out.data, (y * out.width + x) * 4, s, s + 4);
      }
    }
    return out;
  }

  crop(x, y, w, h) {
    const out = new Canvas(w, h);
    for (let j = 0; j < h; j++) {
      for (let i = 0; i < w; i++) {
        if (x + i < 0 || y + j < 0 || x + i >= this.width || y + j >= this.height) continue;
        const s = ((y + j) * this.width + x + i) * 4;
        this.data.copy(out.data, (j * w + i) * 4, s, s + 4);
      }
    }
    return out;
  }

  png() {
    return encodePng({ width: this.width, height: this.height, data: this.data });
  }
}
