#!/usr/bin/env node
// Generates the textures and preview icons of worn cosmetics (original, procedural art):
//   hats:  textures/cosmetics/hats/<id>.png  painted from the box models in medirian/cosmetics/models/<id>.json
//   wings: textures/cosmetics/wings/<id>.png (left wing in the left half; the right wing mirrors it)
//   icons: textures/cosmetics/icons/<id>.png (64×64 previews for the cosmetics screen: hats, wings, trails)
// Capes come from scripts/generate-capes.mjs.
//
// Usage: node scripts/generate-cosmetics.mjs
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { encodePng } from './lib/png.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const RES = join(ROOT, 'client/shared/src/main/resources');
const TEX = join(RES, 'assets/medirian/textures/cosmetics');
const MODELS = join(RES, 'medirian/cosmetics/models');

const hex = (value) => [parseInt(value.slice(1, 3), 16), parseInt(value.slice(3, 5), 16), parseInt(value.slice(5, 7), 16)];
const shade = (rgb, f) => rgb.map((c) => Math.max(0, Math.min(255, Math.round(f >= 1 ? c + (255 - c) * (f - 1) : c * f))));
const hash = (n) => {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
};
const clamp01 = (v) => Math.max(0, Math.min(1, v));

class Canvas {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.data = Buffer.alloc(width * height * 4);
  }

  set(x, y, rgb, a = 1) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height || a <= 0) return;
    const i = (y * this.width + x) * 4;
    const old = this.data[i + 3] / 255;
    const out = a + old * (1 - a);
    for (let c = 0; c < 3; c++) {
      this.data[i + c] = Math.round((rgb[c] * a + this.data[i + c] * old * (1 - a)) / (out || 1));
    }
    this.data[i + 3] = Math.round(out * 255);
  }

  rect(x, y, w, h, fn) {
    for (let py = 0; py < h; py++) {
      for (let px = 0; px < w; px++) {
        const [rgb, a] = fn(px, py, w, h);
        this.set(x + px, y + py, rgb, a);
      }
    }
  }

  /** Fills a convex polygon (anti-aliased by 4×4 supersampling). */
  polygon(points, rgb) {
    const xs = points.map((p) => p[0]);
    const ys = points.map((p) => p[1]);
    const inside = (x, y) => {
      let sign = 0;
      for (let i = 0; i < points.length; i++) {
        const [ax, ay] = points[i];
        const [bx, by] = points[(i + 1) % points.length];
        const cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
        if (cross !== 0) {
          if (sign === 0) sign = Math.sign(cross);
          else if (Math.sign(cross) !== sign) return false;
        }
      }
      return true;
    };
    for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++) {
      for (let x = Math.floor(Math.min(...xs)); x <= Math.ceil(Math.max(...xs)); x++) {
        let hits = 0;
        for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) if (inside(x + (sx + 0.5) / 4, y + (sy + 0.5) / 4)) hits++;
        if (hits) this.set(x, y, rgb, hits / 16);
      }
    }
  }

  png() {
    return encodePng({ width: this.width, height: this.height, data: this.data });
  }
}

// ---------------------------------------------------------------- hats (box models)

function boxRegions(box) {
  const [w, h, d] = (box.uvSize ?? box.size).map(Math.round);
  const [u, v] = box.uv;
  return {
    top: [u + d, v, w, d],
    bottom: [u + d + w, v, w, d],
    right: [u, v + d, d, h],
    front: [u + d, v + d, w, h],
    left: [u + d + w, v + d, d, h],
    back: [u + d + w + d, v + d, w, h]
  };
}

const FACE_LIGHT = { top: 1.18, bottom: 0.6, right: 0.82, front: 1, left: 0.82, back: 0.9 };

function paintHat(id, model) {
  const canvas = new Canvas(model.texture[0], model.texture[1]);
  model.boxes.forEach((box, b) => {
    const base = hex(box.color);
    for (const [face, [x, y, w, h]] of Object.entries(boxRegions(box))) {
      const light = FACE_LIGHT[face];
      canvas.rect(x, y, w, h, (px, py) => {
        // a little texture, darker edges and a soft gradient down the sides
        const noise = 0.94 + hash(b * 97 + px * 13.1 + py * 7.7 + face.length) * 0.1;
        const edge = px === 0 || py === 0 || px === w - 1 || py === h - 1 ? 0.86 : 1;
        const gradient = face === 'top' || face === 'bottom' ? 1 : 1.04 - (py / Math.max(1, h)) * 0.1;
        return [shade(base, light * noise * edge * gradient), 1];
      });
    }
  });
  return canvas;
}

/** Oblique preview: the front, top and right faces of every box, far boxes first. */
function hatIcon(model) {
  const canvas = new Canvas(64, 64);
  const boxes = model.boxes.map((box) => ({ box, from: box.from, size: box.size }));
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  const k = 0.45;
  const project = (x, y, z) => [x + z * k, y - z * k * 0.7];
  for (const { from, size } of boxes) {
    for (const [x, y, z] of [[from[0], from[1], from[2]], [from[0] + size[0], from[1] + size[1], from[2] + size[2]],
      [from[0], from[1], from[2] + size[2]], [from[0] + size[0], from[1], from[2] + size[2]]]) {
      const [px, py] = project(x, y, z);
      minX = Math.min(minX, px); maxX = Math.max(maxX, px); minY = Math.min(minY, py); maxY = Math.max(maxY, py);
    }
  }
  const scale = Math.min(52 / (maxX - minX), 52 / (maxY - minY));
  const ox = 32 - ((minX + maxX) / 2) * scale;
  const oy = 32 - ((minY + maxY) / 2) * scale;
  const map = (x, y, z) => {
    const [px, py] = project(x, y, z);
    return [ox + px * scale, oy + py * scale];
  };
  // back to front: larger z (behind the head) first
  boxes.sort((a, b) => (b.from[2] + b.size[2]) - (a.from[2] + a.size[2]));
  for (const { box, from: [x, y, z], size: [w, h, d] } of boxes) {
    const base = hex(box.color);
    canvas.polygon([map(x, y, z), map(x + w, y, z), map(x + w, y + h, z), map(x, y + h, z)], base);
    canvas.polygon([map(x, y, z + d), map(x + w, y, z + d), map(x + w, y, z), map(x, y, z)], shade(base, 1.25));
    canvas.polygon([map(x + w, y, z), map(x + w, y, z + d), map(x + w, y + h, z + d), map(x + w, y + h, z)], shade(base, 0.75));
  }
  return canvas;
}

// ---------------------------------------------------------------- wings

/**
 * Wing shapes in the 32×32 left half: u = 32 is the root at the spine, u = 0 the tip. All wings
 * share one silhouette: an arched upper edge and a lower edge running from the bottom of the root
 * up to the tip; each design decorates it.
 */
const wingTop = (t) => 7 - 5.5 * Math.sin(Math.PI * Math.min(1, t * 1.15)) + t * 1.5;
const wingBottom = (t) => 29 - 19 * t;

const WINGS = {
  wings_medirian: (u, v) => {
    const t = 1 - u / 32;
    const top = wingTop(t);
    const bottom = wingBottom(t) - 3 * Math.abs(Math.sin(t * Math.PI * 3));
    if (v < top || v > bottom) return [[0, 0, 0], 0];
    // crystal facets: diagonal bands, brighter towards the upper edge, a glowing rim
    const facet = Math.floor((u * 0.6 + v) / 5) % 3;
    const base = [hex('#7a3fc0'), hex('#9b55d6'), hex('#b67ae8')][facet];
    const rim = Math.min(v - top, bottom - v) < 1.2 ? 1.35 : 1;
    return [shade(base, rim * (1.1 - (v - top) / 60)), 0.94];
  },
  wings_angel: (u, v) => {
    const t = 1 - u / 32;
    const top = wingTop(t);
    // feathered lower edge: a saw tooth along the diagonal
    const teeth = (t * 9) % 1;
    const bottom = wingBottom(t) + 2.5 * (1 - teeth);
    if (v < top || v > bottom) return [[0, 0, 0], 0];
    // three rows of feathers: small coverts at the top, long primaries below
    const depth = (v - top) / Math.max(1, bottom - top);
    const row = depth < 0.35 ? 0 : depth < 0.65 ? 1 : 2;
    const quill = Math.abs(((t * (row === 0 ? 14 : 9)) % 1) - 0.5) < 0.07;
    const base = [hex('#ffffff'), hex('#f1eefa'), hex('#e4e0f2')][row];
    return [shade(base, quill ? 0.86 : 1), 1];
  },
  wings_bat: (u, v) => {
    const t = 1 - u / 32;
    const top = wingTop(t) + 0.5;
    // membrane scalloped between four finger bones
    const scallop = 5 * Math.pow(Math.abs(Math.sin(t * Math.PI * 4)), 0.7);
    const bottom = wingBottom(t) - scallop;
    if (v < top || v > bottom) return [[0, 0, 0], 0];
    // bones radiate from the wrist (root, near the top) to the scallop points
    let bone = v - top < 1.3;
    for (let f = 1; f <= 4; f++) {
      const tipT = f / 4;
      const tipV = wingBottom(tipT);
      const along = t / tipT;
      if (along <= 1) {
        const boneV = top + (tipV - top) * along;
        if (Math.abs(v - boneV) < 0.8) bone = true;
      }
    }
    const membrane = shade(hex('#4e2f5c'), 0.85 + 0.3 * (1 - (v - top) / 30));
    return [bone ? hex('#2b1f33') : membrane, 1];
  }
};

function paintWings(shapeFn) {
  const canvas = new Canvas(64, 32);
  canvas.rect(0, 0, 32, 32, (px, py) => shapeFn(px + 0.5, py + 0.5));
  // the right half stays empty: the mesh mirrors the left wing
  return canvas;
}

function wingsIcon(shapeFn) {
  const canvas = new Canvas(64, 64);
  canvas.rect(0, 16, 32, 32, (px, py) => shapeFn(px + 0.5, py + 0.5));
  canvas.rect(32, 16, 32, 32, (px, py) => shapeFn(32 - px - 0.5, py + 0.5));
  return canvas;
}

// ---------------------------------------------------------------- trails (icons only)

function heart(canvas, cx, cy, r, rgb) {
  canvas.rect(Math.floor(cx - r * 1.2), Math.floor(cy - r * 1.35), Math.ceil(r * 2.4), Math.ceil(r * 2.5), (px, py) => {
    const x = (Math.floor(cx - r * 1.2) + px + 0.5 - cx) / r;
    const y = -(Math.floor(cy - r * 1.35) + py + 0.5 - cy) / r;
    const f = (x * x + y * y - 1) ** 3 - x * x * y * y * y;
    return [rgb, f <= 0 ? 1 : 0];
  });
}

function flame(canvas, cx, cy, r, outer, inner) {
  canvas.rect(Math.floor(cx - r), Math.floor(cy - r * 2), Math.ceil(r * 2), Math.ceil(r * 3), (px, py) => {
    const x = (Math.floor(cx - r) + px + 0.5 - cx) / r;
    const y = (Math.floor(cy - r * 2) + py + 0.5 - cy) / r;
    // teardrop: circle at the bottom, point at the top
    const width = y > 0 ? Math.sqrt(Math.max(0, 1 - y * y)) : 1 + y / 2;
    if (Math.abs(x) > width) return [outer, 0];
    return [Math.abs(x) < width * 0.5 && y > -0.6 ? inner : outer, 1];
  });
}

function sparkle(canvas, cx, cy, r, rgb) {
  canvas.rect(Math.floor(cx - r), Math.floor(cy - r), Math.ceil(r * 2), Math.ceil(r * 2), (px, py) => {
    const x = Math.abs(Math.floor(cx - r) + px + 0.5 - cx) / r;
    const y = Math.abs(Math.floor(cy - r) + py + 0.5 - cy) / r;
    return [rgb, clamp01((1 - (Math.sqrt(x) + Math.sqrt(y))) * 3)];
  });
}

function snowflake(canvas, cx, cy, r, rgb) {
  const seg = (x, y, ax, ay, bx, by) => {
    const dx = bx - ax, dy = by - ay;
    const t = clamp01(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy));
    return Math.hypot(x - ax - dx * t, y - ay - dy * t);
  };
  canvas.rect(Math.floor(cx - r - 1), Math.floor(cy - r - 1), Math.ceil(r * 2 + 2), Math.ceil(r * 2 + 2), (px, py) => {
    const x = Math.floor(cx - r - 1) + px + 0.5;
    const y = Math.floor(cy - r - 1) + py + 0.5;
    let d = Infinity;
    for (let k = 0; k < 6; k++) {
      const a = (Math.PI / 3) * k;
      d = Math.min(d, seg(x, y, cx, cy, cx + Math.cos(a) * r, cy + Math.sin(a) * r));
    }
    return [rgb, clamp01(1.4 - d)];
  });
}

const TRAILS = {
  trail_hearts: (c) => {
    heart(c, 22, 26, 9, hex('#e5566a'));
    heart(c, 44, 40, 7, hex('#ff7d8f'));
    heart(c, 24, 48, 5, hex('#c93c52'));
  },
  trail_flames: (c) => {
    flame(c, 32, 40, 11, hex('#ff6a10'), hex('#ffd36b'));
    flame(c, 16, 48, 6, hex('#ff8a2a'), hex('#ffe08a'));
    flame(c, 48, 50, 5, hex('#ff8a2a'), hex('#ffe08a'));
  },
  trail_snow: (c) => {
    snowflake(c, 24, 24, 11, hex('#dff3ff'));
    snowflake(c, 44, 42, 8, hex('#ffffff'));
    snowflake(c, 20, 48, 5, hex('#bfe6f7'));
  },
  trail_sparkles: (c) => {
    sparkle(c, 28, 28, 14, hex('#f2cf6b'));
    sparkle(c, 46, 44, 9, hex('#c9a4f2'));
    sparkle(c, 18, 48, 6, hex('#ffffff'));
  }
};

// ---------------------------------------------------------------- emotes (icons only)

/** A thick line with round ends. */
function stroke(canvas, ax, ay, bx, by, width, rgb) {
  const x0 = Math.floor(Math.min(ax, bx) - width);
  const y0 = Math.floor(Math.min(ay, by) - width);
  canvas.rect(x0, y0, Math.ceil(Math.abs(bx - ax) + width * 2) + 1, Math.ceil(Math.abs(by - ay) + width * 2) + 1, (px, py) => {
    const x = x0 + px + 0.5;
    const y = y0 + py + 0.5;
    const dx = bx - ax;
    const dy = by - ay;
    const t = clamp01(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy || 1));
    const d = Math.hypot(x - ax - dx * t, y - ay - dy * t);
    return [rgb, clamp01(width / 2 + 0.5 - d)];
  });
}

function disc(canvas, cx, cy, r, rgb) {
  canvas.rect(Math.floor(cx - r - 1), Math.floor(cy - r - 1), Math.ceil(r * 2 + 2), Math.ceil(r * 2 + 2), (px, py) => {
    const d = Math.hypot(Math.floor(cx - r - 1) + px + 0.5 - cx, Math.floor(cy - r - 1) + py + 0.5 - cy);
    return [rgb, clamp01(r + 0.5 - d)];
  });
}

/** A little figure: head, body and limbs given as end points. */
function figure(canvas, arms, legs, rgb) {
  disc(canvas, 32, 16, 6, rgb);
  stroke(canvas, 32, 24, 32, 40, 5, rgb);
  for (const [x, y] of arms) stroke(canvas, 32, 27, x, y, 4, rgb);
  for (const [x, y] of legs) stroke(canvas, 32, 40, x, y, 4, rgb);
}

const EMOTES = {
  emote_wave: (c) => {
    figure(c, [[46, 8], [22, 38]], [[26, 56], [38, 56]], hex('#ece6f6'));
    stroke(c, 50, 4, 54, 8, 2, hex('#b67ae8'));
    stroke(c, 52, 12, 57, 14, 2, hex('#b67ae8'));
  },
  emote_cheer: (c) => {
    figure(c, [[20, 8], [44, 8]], [[26, 56], [38, 56]], hex('#ece6f6'));
    sparkle(c, 12, 14, 5, hex('#f2cf6b'));
    sparkle(c, 52, 14, 5, hex('#f2cf6b'));
  },
  emote_dance: (c) => {
    figure(c, [[18, 22], [46, 34]], [[24, 54], [42, 50]], hex('#ece6f6'));
    // two music notes
    stroke(c, 50, 6, 50, 18, 2, hex('#b67ae8'));
    stroke(c, 50, 6, 56, 8, 2, hex('#b67ae8'));
    disc(c, 48, 19, 2.6, hex('#b67ae8'));
    stroke(c, 12, 36, 12, 46, 2, hex('#9b55d6'));
    disc(c, 10, 47, 2.4, hex('#9b55d6'));
  }
};

// ---------------------------------------------------------------- output

for (const dir of ['hats', 'wings', 'icons']) mkdirSync(join(TEX, dir), { recursive: true });

for (const file of readdirSync(MODELS).filter((f) => f.startsWith('hat_') && f.endsWith('.json'))) {
  const id = file.slice(0, -5);
  const model = JSON.parse(readFileSync(join(MODELS, file), 'utf8'));
  writeFileSync(join(TEX, 'hats', `${id}.png`), paintHat(id, model).png());
  writeFileSync(join(TEX, 'icons', `${id}.png`), hatIcon(model).png());
  console.log(`hat ${id}`);
}
for (const [id, shapeFn] of Object.entries(WINGS)) {
  writeFileSync(join(TEX, 'wings', `${id}.png`), paintWings(shapeFn).png());
  writeFileSync(join(TEX, 'icons', `${id}.png`), wingsIcon(shapeFn).png());
  console.log(`wings ${id}`);
}
for (const [id, draw] of Object.entries({ ...TRAILS, ...EMOTES })) {
  const canvas = new Canvas(64, 64);
  draw(canvas);
  writeFileSync(join(TEX, 'icons', `${id}.png`), canvas.png());
  console.log(`icon ${id}`);
}
