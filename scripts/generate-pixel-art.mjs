#!/usr/bin/env node
// Generates Medirian Client's pixel-art identity from code (scripts/pixel/): the Halloween night
// scene, the logo, the icons, the app icon and the Medirian Pixel font. All art is original.
//
//   client  assets/medirian/textures/gui/scene/{sky,far,forest,ground,glow,fog}.png  (480×270 title screen)
//           assets/medirian/textures/gui/{logo,mark,icons,bat}.png + gui/icons.txt (atlas order)
//           assets/medirian/icon.png (mod icon)
//   launcher src/renderer/assets/scene/*.png (400×250), logo.png, mark.png, icon.png,
//           src/renderer/assets/icons/<name>.png (colour icons), src/renderer/assets/fonts/medirian-pixel.ttf,
//           resources/icon.png (app icon, 512 px), resources/{installer,uninstaller}Sidebar.bmp and
//           resources/installerHeader.bmp (Windows installer)
//   website  assets/scene/{sky,far,forest,ground,fog}.png (hero, 480×270 parallax layers), logo.png, icon.png,
//           icons.png + icons.css (icon sprite), capes/*.png and cosmetics/*.png (from the client's cosmetics),
//           skin-back.png, social.png (1200×630 link preview), medirian-pixel.ttf
//   branding medirian-icon-{512,256,128}.png, medirian-logo.png
//
// Usage: node scripts/generate-pixel-art.mjs
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng } from './lib/png.mjs';
import { Canvas, bayer, hash } from './pixel/canvas.mjs';
import { buildTtf } from './pixel/font.mjs';
import { ALL_ICONS, icon } from './pixel/icons.mjs';
import { chunkyText, lockup, mark, text } from './pixel/logo.mjs';
import { PAL } from './pixel/palette.mjs';
import { PUMPKIN, buildScene, flatten } from './pixel/scene.mjs';
import { defaultSkin } from './pixel/skin.mjs';

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

/**
 * The installer's welcome/finish sidebar (164×314, NSIS' fixed size): the cabin corner of the
 * night scene at 2× with the mark and the wordmark above it.
 */
function installerSidebar() {
  const W = 82;
  const H = 157;
  const scene = flatten(buildScene(200, H, { moon: [166, 70, 11] }));
  const c = scene.crop(200 - W, 0, W, H);
  const m = mark(ROOT, 22);
  c.draw(m, Math.round((W - m.width) / 2), 10);
  const word = chunkyText('MEDIRIAN', { scale: 1, top: PAL.lavender, bottom: PAL.lilac, side: PAL.purple, depth: 1 });
  c.draw(word, Math.round((W - word.width) / 2), 12 + m.height);
  const client = text('CLIENT', PAL.pumpkinLight, { outline: PAL.ink });
  c.draw(client, Math.round((W - client.width) / 2), 12 + m.height + word.height - 1);
  return c.scaled(2);
}

/** The header of the installer's inner pages (150×57): the mark on the night sky. */
function installerHeader() {
  const c = new Canvas(150, 57);
  const stops = [PAL.night2, PAL.night1, PAL.night0];
  for (let y = 0; y < 57; y++) {
    for (let x = 0; x < 150; x++) {
      const f = (x / 150) * (stops.length - 1);
      const k = Math.floor(f);
      c.set(x, y, f - k > bayer(x, y) ? stops[Math.min(stops.length - 1, k + 1)] : stops[k]);
    }
  }
  const m = mark(ROOT, 22);
  const big = m.scaled(2);
  c.draw(big, 150 - big.width - 6, Math.round((57 - big.height) / 2));
  return c;
}

/** A 24-bit BMP (what NSIS' wizard images must be), opaque pixels over the darkest night tone. */
function bmp(canvas) {
  const { width, height, data } = canvas;
  const stride = Math.ceil((width * 3) / 4) * 4;
  const file = Buffer.alloc(54 + stride * height);
  file.write('BM', 0);
  file.writeUInt32LE(file.length, 2);
  file.writeUInt32LE(54, 10);
  file.writeUInt32LE(40, 14);
  file.writeInt32LE(width, 18);
  file.writeInt32LE(height, 22);
  file.writeUInt16LE(1, 26);
  file.writeUInt16LE(24, 28);
  file.writeUInt32LE(stride * height, 34);
  const [br, bg, bb] = [0x0c, 0x08, 0x14];
  for (let y = 0; y < height; y++) {
    const row = 54 + (height - 1 - y) * stride;
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const a = data[i + 3] / 255;
      file[row + x * 3] = Math.round(data[i + 2] * a + bb * (1 - a));
      file[row + x * 3 + 1] = Math.round(data[i + 1] * a + bg * (1 - a));
      file[row + x * 3 + 2] = Math.round(data[i] * a + br * (1 - a));
    }
  }
  return file;
}

// ------------------------------------------------------------------ client
writeScene(join(GUI, 'scene'), 480, 270);
out(join(GUI, 'logo.png'), lockup(ROOT).png());
out(join(GUI, 'mark.png'), mark(ROOT, 30).png());
out(join(GUI, 'icons.png'), iconAtlas().png());
out(join(GUI, 'icons.txt'), ALL_ICONS.join('\n') + '\n');
out(join(GUI, 'bat.png'), bat().png());
out(join(GUI, 'default_skin.png'), defaultSkin().png());
const app = appIcon();
out(join(ROOT, 'client/shared/src/main/resources/assets/medirian/icon.png'), app.scaled(2).png());

// ------------------------------------------------------------------ launcher
// the launcher's news board sits top right, so its moon rises further left
writeScene(join(ASSETS, 'scene'), 400, 250, { moon: [226, 70, 20] });
out(join(ASSETS, 'logo.png'), lockup(ROOT).png());
out(join(ASSETS, 'mark.png'), mark(ROOT, 30).png());
out(join(ASSETS, 'bat.png'), bat().png());
out(join(ASSETS, 'snow.png'), snowTile().png());
out(join(ASSETS, 'default-skin.png'), defaultSkin().png());
out(join(ASSETS, 'icon.png'), app.scaled(4).png());
out(join(LAUNCHER, 'resources/icon.png'), app.scaled(8).png());
for (const name of ALL_ICONS) out(join(ASSETS, 'icons', `${name}.png`), (name === 'mods' ? markIcon() : icon(name)).png());
out(join(ASSETS, 'fonts/medirian-pixel.ttf'), buildTtf());

// ------------------------------------------------------------------ Windows installer
out(join(LAUNCHER, 'resources/installerSidebar.bmp'), bmp(installerSidebar()));
out(join(LAUNCHER, 'resources/uninstallerSidebar.bmp'), bmp(installerSidebar()));
out(join(LAUNCHER, 'resources/installerHeader.bmp'), bmp(installerHeader()));

// ------------------------------------------------------------------ website (website/)
{
  const WEB = join(ROOT, 'website/assets');
  const COSMETICS = join(ROOT, 'client/shared/src/main/resources/assets/medirian/textures/cosmetics');
  const load = (file) => {
    const { width, height, data } = decodePng(readFileSync(file));
    const c = new Canvas(width, height);
    data.copy(c.data);
    return c;
  };
  // files of the earlier download page that the website no longer uses
  for (const name of ['scene.png', 'fog.png', ...['mods', 'cosmetics', 'profiles', 'hudedit', 'fps', 'news', 'play', 'singleplayer'].map((n) => `icon-${n}.png`)]) {
    rmSync(join(WEB, name), { force: true });
  }

  // the hero: the same night as the game's title screen, in layers the page moves at different speeds;
  // the moon sits top right so the centred title has the open sky
  const scene = buildScene(480, 270, { moon: [392, 50, 22] });
  for (const name of ['sky', 'far', 'forest', 'fog']) out(join(WEB, 'scene', `${name}.png`), scene[name].png());
  const ground = new Canvas(480, 270);
  ground.draw(scene.ground, 0, 0);
  ground.draw(scene.glow, 0, 0);
  out(join(WEB, 'scene', 'ground.png'), ground.png());

  out(join(WEB, 'logo.png'), lockup(ROOT).png());
  out(join(WEB, 'icon.png'), app.scaled(2).png());
  out(join(WEB, 'medirian-pixel.ttf'), buildTtf());

  // icon sprite: one row of 16×16 cells, and the classes that pick a cell
  const WEB_ICONS = ['cat-performance', 'cat-hud', 'cat-all', 'mods', 'profiles', 'cosmetics', 'settings', 'cat-world', 'keystrokes', 'cps',
    'fps', 'ping', 'coordinates', 'targethud', 'sessioninfo', 'fullbright', 'blockoverlay', 'cat-render', 'search', 'play', 'cat-misc',
    'moon', 'news', 'language', 'entityculling', 'dynamicfps', 'particles', 'waypoints', 'hudedit', 'close'];
  const sprite = new Canvas(WEB_ICONS.length * 16, 16);
  WEB_ICONS.forEach((name, i) => sprite.draw(name === 'mods' ? markIcon() : icon(name), i * 16, 0));
  out(join(WEB, 'icons.png'), sprite.png());
  out(join(WEB, 'icons.css'), Buffer.from([
    '/* Generated by scripts/generate-pixel-art.mjs: the icon sprite (icons.png), one 16×16 cell per icon.',
    '   <span class="ico ico--fps"></span>; --s scales it (2 = 32 px). */',
    `.ico{display:inline-block;flex:none;width:calc(16px*var(--s,2));height:calc(16px*var(--s,2));background:url('icons.png') 0 0/calc(${WEB_ICONS.length * 16}px*var(--s,2)) auto no-repeat;image-rendering:pixelated}`,
    ...WEB_ICONS.map((name, i) => `.ico--${name}{background-position:calc(${-i * 16}px*var(--s,2)) 0}`),
    ''
  ].join('\n')));

  // capes as seen from behind the player: the outer face of the cape (texture cell 1,1 – 10×16, 4× HD)
  for (const id of ['medirian', 'moonlit', 'aurora', 'ember', 'frost']) {
    out(join(WEB, 'capes', `${id}.png`), load(join(COSMETICS, 'capes', `cape_${id}.png`)).crop(4, 4, 40, 64).png());
  }
  for (const id of ['hat_tophat', 'wings_medirian', 'trail_sparkles', 'emote_wave']) {
    out(join(WEB, 'cosmetics', `${id}.png`), load(join(COSMETICS, 'icons', `${id}.png`)).png());
  }

  // Medirian's default skin from behind, flat (16×32): head, body, arms and legs back faces
  const skin = defaultSkin();
  const back = new Canvas(16, 32);
  const face = (u, v, w, h, x, y) => back.draw(skin.crop(u, v, w, h), x, y);
  face(24, 8, 8, 8, 4, 0); // head
  face(56, 8, 8, 8, 4, 0); // hood (hat layer)
  face(32, 20, 8, 12, 4, 8); // body
  // seen from behind, the player's right arm and leg are on the right
  face(52, 20, 4, 12, 12, 8); // right arm
  face(44, 52, 4, 12, 0, 8); // left arm
  face(12, 20, 4, 12, 8, 20); // right leg
  face(28, 52, 4, 12, 4, 20); // left leg
  out(join(WEB, 'skin-back.png'), back.png());

  // link preview (Open Graph): the night at 3×, the logo in the sky
  {
    const small = flatten(buildScene(400, 210, { moon: [330, 44, 18] }));
    const logo = lockup(ROOT).scaled(2);
    small.draw(logo, Math.round((400 - logo.width) / 2), 70);
    out(join(WEB, 'social.png'), small.scaled(3).png());
  }
}

// ------------------------------------------------------------------ branding
for (const size of [512, 256, 128]) out(join(ROOT, `branding/medirian-icon-${size}.png`), app.scaled(size / 64).png());
out(join(ROOT, 'branding/medirian-logo.png'), lockup(ROOT).scaled(4).png());
