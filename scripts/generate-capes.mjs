#!/usr/bin/env node
// Generates Medirian's cape textures (original, procedural art) into
// client/shared/src/main/resources/assets/medirian/textures/cosmetics/capes/<id>.png
//
// Layout: the vanilla 64×32 cape texture at 4× (256×128) — Minecraft normalises cape UVs, so HD
// textures work in 1.8.9 and 1.21.11. Painted regions: the cape box (10×16×1 at 0,0) and the
// elytra box (10×20×2 at 22,0), which 1.21 uses when a cape has no separate elytra texture.
//
// Usage: node scripts/generate-capes.mjs
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from './lib/png.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OUT = join(ROOT, 'client/shared/src/main/resources/assets/medirian/textures/cosmetics/capes');
const S = 4;
const W = 64 * S;
const H = 32 * S;

const MARK = decodePng(readFileSync(join(ROOT, 'client/shared/src/main/resources/assets/medirian/textures/gui/mark.png')));

const hex = (value) => [parseInt(value.slice(1, 3), 16), parseInt(value.slice(3, 5), 16), parseInt(value.slice(5, 7), 16)];
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const clamp01 = (v) => Math.max(0, Math.min(1, v));
const smooth = (e0, e1, x) => {
  const t = clamp01((x - e0) / (e1 - e0));
  return t * t * (3 - 2 * t);
};
/** Deterministic pseudo-random number in [0, 1). */
const hash = (n) => {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
};
/** Anti-aliased disc coverage of a pixel at distance d from the centre (in pixels). */
const disc = (d, r) => clamp01(r + 0.5 - d);
/** Draws {@code over} with coverage {@code a} on top of {@code base}. */
const over = (base, color, a) => mix(base, color, clamp01(a));

/** Bilinear sample of the Medirian mark's alpha at normalised coordinates. */
function markAlpha(u, v) {
  if (u < 0 || u > 1 || v < 0 || v > 1) return 0;
  const x = u * (MARK.width - 1);
  const y = v * (MARK.height - 1);
  const x0 = Math.floor(x);
  const y0 = Math.floor(y);
  const x1 = Math.min(MARK.width - 1, x0 + 1);
  const y1 = Math.min(MARK.height - 1, y0 + 1);
  const a = (px, py) => MARK.data[(py * MARK.width + px) * 4 + 3] / 255;
  const top = a(x0, y0) + (a(x1, y0) - a(x0, y0)) * (x - x0);
  const bottom = a(x0, y1) + (a(x1, y1) - a(x0, y1)) * (x - x0);
  return top + (bottom - top) * (y - y0);
}

/** The mark centred at (cx, cy) with the given width, all in face pixels. */
function mark(x, y, cx, cy, width) {
  const height = width * MARK.height / MARK.width;
  return markAlpha((x - cx) / width + 0.5, (y - cy) / height + 0.5);
}

function stars(x, y, w, h, count, seed, color, base) {
  let c = base;
  for (let i = 0; i < count; i++) {
    const sx = hash(seed + i * 3.1) * w;
    const sy = hash(seed + i * 7.7) * h;
    const r = 0.35 + hash(seed + i * 1.3) * 0.9;
    const d = Math.hypot(x - sx, y - sy);
    c = over(c, color, disc(d, r) * (0.5 + 0.5 * hash(seed + i)));
  }
  return c;
}

/** Thin line segment coverage (for snowflakes). */
function segment(x, y, ax, ay, bx, by, width) {
  const dx = bx - ax;
  const dy = by - ay;
  const t = clamp01(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy));
  return clamp01(width / 2 + 0.5 - Math.hypot(x - (ax + dx * t), y - (ay + dy * t)));
}

function snowflake(x, y, cx, cy, r) {
  let a = 0;
  for (let k = 0; k < 6; k++) {
    const angle = (Math.PI / 3) * k;
    const ex = cx + Math.cos(angle) * r;
    const ey = cy + Math.sin(angle) * r;
    a = Math.max(a, segment(x, y, cx, cy, ex, ey, r * 0.16));
    // small branches two thirds out
    const bx = cx + Math.cos(angle) * r * 0.6;
    const by = cy + Math.sin(angle) * r * 0.6;
    for (const side of [-1, 1]) {
      const ba = angle + side * 0.75;
      a = Math.max(a, segment(x, y, bx, by, bx + Math.cos(ba) * r * 0.35, by + Math.sin(ba) * r * 0.35, r * 0.12));
    }
  }
  return a;
}

/** Each design paints a face of w×h pixels; (x, y) is the pixel centre. */
const DESIGNS = {
  cape_medirian: {
    edge: hex('#1a0f2c'),
    paint(x, y, w, h) {
      const v = y / h;
      let c = mix(hex('#2d184c'), hex('#0e0918'), v);
      // a soft diagonal sheen
      c = over(c, hex('#4a2a78'), 0.25 * smooth(0.25, 0, Math.abs((x / w) - v * 0.6 - 0.2)));
      c = over(c, hex('#9b55d6'), 0.35 * smooth(0.9, 1, v));
      // accent border
      const border = Math.min(x, w - x, h - y);
      c = over(c, hex('#9b55d6'), clamp01(S * 0.5 + 0.5 - border));
      const m = mark(x, y, w / 2, h * 0.36, w * 0.68);
      c = over(c, hex('#9b55d6'), mark(x, y + S * 0.4, w / 2, h * 0.36, w * 0.68) * 0.6);
      return over(c, hex('#ece6f6'), m);
    }
  },
  cape_moonlit: {
    edge: hex('#0b0f26'),
    paint(x, y, w, h) {
      let c = mix(hex('#1c2656'), hex('#080b1c'), y / h);
      c = stars(x, y, w, h, 26, 11, hex('#f6f1dc'), c);
      const mx = w * 0.62;
      const my = h * 0.24;
      const r = w * 0.2;
      const d = Math.hypot(x - mx, y - my);
      c = over(c, hex('#6f7fc4'), 0.35 * smooth(r * 2.2, r, d));
      const crescent = disc(d, r) * (1 - disc(Math.hypot(x - mx - r * 0.45, y - my + r * 0.25), r * 0.85));
      c = over(c, hex('#f3ecd2'), crescent);
      // a thin horizon of hills
      const hill = h * (0.86 + 0.04 * Math.sin(x / w * 5.5 + 1) + 0.025 * Math.sin(x / w * 13));
      return over(c, hex('#05060f'), clamp01(y - hill + 0.5));
    }
  },
  cape_ember: {
    edge: hex('#140805'),
    paint(x, y, w, h) {
      const v = y / h;
      const u = x / w;
      let c = mix(hex('#120807'), hex('#3b1307'), v);
      // flames rising from the hem
      const flame = 0.66 - 0.09 * Math.sin(u * 19 + 0.5) - 0.06 * Math.sin(u * 37 + 2) - 0.04 * Math.sin(u * 7);
      const inFlame = smooth(flame - 0.02, flame + 0.06, v);
      c = over(c, mix(hex('#ff6a10'), hex('#ffd36b'), smooth(flame + 0.05, 1, v)), inFlame);
      // embers
      for (let i = 0; i < 14; i++) {
        const ex = hash(i * 5.3 + 1) * w;
        const ey = hash(i * 2.9 + 4) * h * 0.62;
        c = over(c, hex('#ffb347'), disc(Math.hypot(x - ex, y - ey), 0.6 + hash(i) * 0.8) * 0.85);
      }
      return c;
    }
  },
  cape_frost: {
    edge: hex('#2d5f8c'),
    paint(x, y, w, h) {
      let c = mix(hex('#d9f2fb'), hex('#3f7db3'), y / h);
      const flakes = [[0.3, 0.2, 0.17], [0.72, 0.42, 0.12], [0.36, 0.66, 0.1], [0.75, 0.82, 0.14], [0.14, 0.9, 0.07], [0.85, 0.12, 0.07]];
      for (const [fx, fy, fr] of flakes) {
        c = over(c, hex('#ffffff'), snowflake(x, y, fx * w, fy * h, fr * w) * 0.95);
      }
      c = stars(x, y, w, h, 20, 77, hex('#ffffff'), c);
      const border = Math.min(x, w - x, h - y);
      return over(c, hex('#f4fbff'), clamp01(S * 0.5 + 0.5 - border) * 0.9);
    }
  },
  cape_aurora: {
    edge: hex('#06131a'),
    paint(x, y, w, h) {
      const u = x / w;
      const v = y / h;
      let c = mix(hex('#081a24'), hex('#03080c'), v);
      c = stars(x, y, w, h, 18, 5, hex('#e9f7ff'), c);
      const bands = [
        [0.3, 0.07, 6.5, 0.4, '#3cf2a5', 0.8],
        [0.42, 0.09, 4.2, 2.1, '#9b55d6', 0.6],
        [0.55, 0.06, 8.3, 1.2, '#4fd1ff', 0.45]
      ];
      for (const [center, amp, freq, phase, color, strength] of bands) {
        const cy = center + amp * Math.sin(u * freq + phase);
        const d = (v - cy) / 0.07;
        // curtains: brighter streaks below the band
        const curtain = Math.exp(-d * d) + (v > cy ? 0.5 * Math.exp(-(((v - cy) / 0.2) ** 2)) * (0.6 + 0.4 * Math.sin(u * 60 + phase)) : 0);
        c = over(c, hex(color), clamp01(curtain * strength));
      }
      return c;
    }
  },
  cape_founder: {
    edge: hex('#0b0906'),
    paint(x, y, w, h) {
      const u = x / w;
      const v = y / h;
      let c = mix(hex('#17130c'), hex('#070604'), v);
      // a fine diamond lattice
      const lattice = Math.abs(((u * 6 + v * 3.75) % 1) - 0.5) < 0.03 || Math.abs(((u * 6 - v * 3.75 + 10) % 1) - 0.5) < 0.03;
      if (lattice) c = over(c, hex('#3a2e14'), 0.6);
      const border = Math.min(x, w - x, h - y);
      c = over(c, hex('#e6b84a'), clamp01(S * 0.75 + 0.5 - border));
      c = over(c, hex('#e6b84a'), clamp01(S * 0.25 + 0.5 - Math.abs(border - S * 1.6)) * 0.7);
      return over(c, hex('#f2cf6b'), mark(x, y, w / 2, h * 0.4, w * 0.62));
    }
  }
};

function paintCape(design) {
  const data = Buffer.alloc(W * H * 4);
  const put = (x, y, rgb) => {
    const i = (y * W + x) * 4;
    data[i] = Math.round(rgb[0]);
    data[i + 1] = Math.round(rgb[1]);
    data[i + 2] = Math.round(rgb[2]);
    data[i + 3] = 255;
  };
  /** Fills a face (unit coordinates) with the design, or with the edge colour. */
  const face = (ux, uy, uw, uh, painted) => {
    for (let py = 0; py < uh * S; py++) {
      for (let px = 0; px < uw * S; px++) {
        put(ux * S + px, uy * S + py, painted ? design.paint(px + 0.5, py + 0.5, uw * S, uh * S) : design.edge);
      }
    }
  };
  // cape box 10×16×1 at (0,0): top, bottom, sides, outside (1,1) and inside (12,1)
  face(1, 0, 10, 1, false);
  face(11, 0, 10, 1, false);
  face(0, 1, 1, 16, false);
  face(11, 1, 1, 16, false);
  face(1, 1, 10, 16, true);
  face(12, 1, 10, 16, false);
  // elytra box 10×20×2 at (22,0): outside (24,2), inside (36,2), edges
  face(24, 0, 10, 2, false);
  face(34, 0, 10, 2, false);
  face(22, 2, 2, 20, false);
  face(34, 2, 2, 20, false);
  face(24, 2, 10, 20, true);
  face(36, 2, 10, 20, true);
  return { width: W, height: H, data };
}

mkdirSync(OUT, { recursive: true });
for (const [id, design] of Object.entries(DESIGNS)) {
  writeFileSync(join(OUT, `${id}.png`), encodePng(paintCape(design)));
  console.log(`wrote ${id}.png`);
}
