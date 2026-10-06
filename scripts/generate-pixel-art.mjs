#!/usr/bin/env node
// Generates Medirian Client's pixel-art identity from code (scripts/pixel/): the Halloween night
// scene, the logo, the icons, the app icon and the Medirian Pixel font. All art is original.
//
//   client  assets/medirian/textures/gui/scene/{sky,far,forest,ground,glow,fog}.png  (480×270 title screen)
//           assets/medirian/textures/gui/{logo,mark,icons,bat}.png + gui/icons.txt (atlas order)
//           assets/medirian/icon.png (mod icon)
//   launcher src/renderer/assets/scene/*.png (400×250), logo.png, mark.png, icon.png,
//           src/renderer/assets/icons/<name>.png (colour icons), src/renderer/assets/fonts/medirian-pixel.ttf,
//           resources/icon.png (app icon, 512 px)
//   branding medirian-icon-{512,256,128}.png, medirian-logo.png
//
// Usage: node scripts/generate-pixel-art.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Canvas, bayer, hash } from './pixel/canvas.mjs';
import { buildTtf } from './pixel/font.mjs';
import { ALL_ICONS, icon } from './pixel/icons.mjs';
import { lockup, mark } from './pixel/logo.mjs';
import { PAL } from './pixel/palette.mjs';
import { PUMPKIN, buildScene } from './pixel/scene.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const GUI = join(ROOT, 'client/shared/src/main/resources/assets/medirian/textures/gui');
const LAUNCHER = join(ROOT, 'launcher');
const ASSETS = join(LAUNCHER, 'src/renderer/assets');

const out = (file, data) => {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, data);
  console.log('wrote', file.slice(ROOT.length + 1));
};

function writeScene(dir, W, H, options) {
  const scene = buildScene(W, H, options);
  for (const [name, layer] of Object.entries(scene)) out(join(dir, `${name}.png`), layer.png());
}

/** The app icon: night tile, moon, the mark and a pumpkin, 64×64 pixels. */
function appIcon() {
  const S = 64;
  const c = new Canvas(S, S);
  const r = 9; // corner cut
  const inside = (x, y) => {
    const cx = x < r ? r - x : x >= S - r ? x - (S - r - 1) : 0;
    const cy = y < r ? r - y : y >= S - r ? y - (S - r - 1) : 0;
    return cx * cx + cy * cy <= r * r;
  };
  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      if (!inside(x, y)) continue;
      const t = y / S;
      const stops = [PAL.night1, PAL.night2, PAL.night3, PAL.night4, PAL.night5];
      const f = t * (stops.length - 1);
      const k = Math.floor(f);
      c.set(x, y, f - k > bayer(x, y) ? stops[Math.min(stops.length - 1, k + 1)] : stops[k]);
    }
  }
  for (let i = 0; i < 18; i++) {
    const x = 4 + Math.floor(hash(i, 5) * 56);
    const y = 4 + Math.floor(hash(i, 6) * 26);
    if (inside(x, y)) c.set(x, y, hash(i, 7) > 0.6 ? PAL.text : PAL.textMuted);
  }
  // moon, upper right
  c.disc(48, 14, 6, PAL.moon);
  c.disc(50, 16, 2, PAL.moonShade);
  c.set(46, 11, PAL.moonShade);
  // hills
  for (let x = 0; x < S; x++) {
    const h = Math.round(50 + Math.sin(x * 0.12) * 2 + Math.sin(x * 0.31 + 1) * 1.5);
    for (let y = h; y < S; y++) if (inside(x, y)) c.set(x, y, y === h ? PAL.night6 : PAL.night5);
  }
  const m = mark(ROOT, 44);
  c.draw(m, Math.round((S - m.width) / 2), 20);
  c.sprite(PUMPKIN.rows, PUMPKIN.legend, 44, 47);
  // ink rim
  const rim = new Canvas(S, S);
  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      if (inside(x, y) && (!inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1))) rim.set(x, y, PAL.ink);
    }
  }
  c.draw(rim, 0, 0);
  return c;
}

function iconAtlas() {
  const cols = 16;
  const atlas = new Canvas(cols * 16, Math.ceil(ALL_ICONS.length / cols) * 16);
  ALL_ICONS.forEach((name, k) => {
    const art = name === 'mods' ? markIcon() : icon(name);
    atlas.draw(art, (k % cols) * 16, Math.floor(k / cols) * 16);
  });
  return atlas;
}

/** The mark, fitted into a 16×16 icon cell. */
function markIcon() {
  const m = mark(ROOT, 14);
  const c = new Canvas(16, 16);
  c.draw(m, Math.floor((16 - m.width) / 2), Math.floor((16 - m.height) / 2));
  return c;
}

/** A tileable patch of snowflakes (1–2 px), for the launcher's winter snow. */
function snowTile() {
  const c = new Canvas(120, 120);
  for (let i = 0; i < 26; i++) {
    const x = Math.floor(hash(i, 41) * 118);
    const y = Math.floor(hash(i, 42) * 118);
    const big = hash(i, 43) > 0.6;
    const color = hash(i, 44) > 0.5 ? '#eaf4fb' : '#c9d6e8';
    c.rect(x, y, big ? 2 : 1, big ? 2 : 1, color);
  }
  return c;
}

const BAT = ['.k.......k.', 'kkk.k.k.kkk', 'kkkkkkkkkkk', '.kk.kkk.kk.', '.....k.....'];
function bat() {
  const c = new Canvas(11, 5);
  c.sprite(BAT, { k: PAL.ink }, 0, 0);
  return c;
}

// ------------------------------------------------------------------ client
writeScene(join(GUI, 'scene'), 480, 270);
out(join(GUI, 'logo.png'), lockup(ROOT).png());
out(join(GUI, 'mark.png'), mark(ROOT, 30).png());
out(join(GUI, 'icons.png'), iconAtlas().png());
out(join(GUI, 'icons.txt'), ALL_ICONS.join('\n') + '\n');
out(join(GUI, 'bat.png'), bat().png());
const app = appIcon();
out(join(ROOT, 'client/shared/src/main/resources/assets/medirian/icon.png'), app.scaled(2).png());

// ------------------------------------------------------------------ launcher
// the launcher's news board sits top right, so its moon rises further left
writeScene(join(ASSETS, 'scene'), 400, 250, { moon: [226, 70, 20] });
out(join(ASSETS, 'logo.png'), lockup(ROOT).png());
out(join(ASSETS, 'mark.png'), mark(ROOT, 30).png());
out(join(ASSETS, 'bat.png'), bat().png());
out(join(ASSETS, 'snow.png'), snowTile().png());
out(join(ASSETS, 'icon.png'), app.scaled(4).png());
out(join(LAUNCHER, 'resources/icon.png'), app.scaled(8).png());
for (const name of ALL_ICONS) out(join(ASSETS, 'icons', `${name}.png`), (name === 'mods' ? markIcon() : icon(name)).png());
out(join(ASSETS, 'fonts/medirian-pixel.ttf'), buildTtf());

// ------------------------------------------------------------------ branding
for (const size of [512, 256, 128]) out(join(ROOT, `branding/medirian-icon-${size}.png`), app.scaled(size / 64).png());
out(join(ROOT, 'branding/medirian-logo.png'), lockup(ROOT).scaled(4).png());
