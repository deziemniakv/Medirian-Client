// Medirian's default player skin (original art, standard 64×64 Minecraft skin layout): a wanderer
// in a purple hoodie with a pumpkin pin. Shown when no Minecraft skin is available.
import { Canvas, hash } from './canvas.mjs';
import { PAL } from './palette.mjs';

/**
 * Paints a cuboid in skin layout at (u, v): width w, height h, depth d. {@code paint(face, x, y, fw, fh)}
 * returns the colour of a pixel of a face ('top', 'bottom', 'right', 'front', 'left', 'back').
 */
function cuboid(c, u, v, w, h, d, paint) {
  const faces = {
    top: [u + d, v, w, d],
    bottom: [u + d + w, v, w, d],
    right: [u, v + d, d, h],
    front: [u + d, v + d, w, h],
    left: [u + d + w, v + d, d, h],
    back: [u + 2 * d + w, v + d, w, h]
  };
  for (const [face, [fx, fy, fw, fh]] of Object.entries(faces)) {
    for (let y = 0; y < fh; y++) {
      for (let x = 0; x < fw; x++) {
        const color = paint(face, x, y, fw, fh);
        if (color) c.set(fx + x, fy + y, color);
      }
    }
  }
}

const SKIN = '#e0ad8a';
const SKIN_SHADE = '#c98f6c';
const HAIR = '#3a2236';
const HOOD = PAL.violet;
const HOOD_DARK = PAL.purple;
const HOOD_LIGHT = PAL.amethyst;
const PANTS = '#2a2335';
const PANTS_DARK = '#1d1826';
const BOOT = PAL.wood;
const BOOT_DARK = PAL.woodDark;

const noise = (a, b, c) => hash(a, b, c) > 0.8;

export function defaultSkin() {
  const c = new Canvas(64, 64);
  // head: face in front, hair elsewhere
  cuboid(c, 0, 0, 8, 8, 8, (face, x, y) => {
    if (face === 'front') {
      if (y < 2) return HAIR;
      if (y === 2 && (x === 0 || x === 7)) return HAIR;
      if (y === 4 && (x === 1 || x === 5)) return '#ffffff';
      if (y === 4 && (x === 2 || x === 6)) return PAL.violet;
      if (y === 6 && x >= 3 && x <= 4) return SKIN_SHADE;
      return SKIN;
    }
    if (face === 'right' || face === 'left') return y < 3 || (face === 'right' ? x < 2 : x > 5) ? HAIR : SKIN;
    if (face === 'bottom') return SKIN_SHADE;
    return noise(x, y, 1) ? '#4a2c46' : HAIR;
  });
  // hood (hat layer): around the head, the face left open
  cuboid(c, 32, 0, 8, 8, 8, (face, x, y, fw, fh) => {
    if (face === 'front') return y === 0 || x === 0 || x === fw - 1 ? (y === 0 ? HOOD_LIGHT : HOOD) : null;
    if (face === 'bottom') return null;
    if (face === 'top') return noise(x, y, 2) ? HOOD_LIGHT : HOOD;
    if (face === 'back') return y === fh - 1 ? HOOD_DARK : noise(x, y, 3) ? HOOD_DARK : HOOD;
    return y >= fh - 2 ? null : x === (face === 'right' ? fw - 1 : 0) ? HOOD_LIGHT : HOOD;
  });
  // body: hoodie with zipper, pocket and a pumpkin pin
  cuboid(c, 16, 16, 8, 12, 4, (face, x, y, fw, fh) => {
    if (face === 'front') {
      if (y === 2 && x === 5) return PAL.pumpkin;
      if (y === 1 && x === 5) return PAL.greenDark;
      if (x === 3 && y < 9) return HOOD_DARK;
      if (y >= 7 && y <= 9 && x >= 1 && x <= 6 && (y === 7 || x === 1 || x === 6)) return HOOD_DARK;
      if (y === fh - 1) return HOOD_DARK;
      return noise(x, y, 4) ? HOOD_LIGHT : HOOD;
    }
    if (face === 'top') return y < 2 && x > 1 && x < 6 ? HOOD_DARK : HOOD;
    return y === fh - 1 ? HOOD_DARK : noise(x, y, 5) ? HOOD_DARK : HOOD;
  });
  // arms: sleeves and hands
  const arm = (face, x, y, fw, fh) => {
    if (face === 'bottom') return SKIN_SHADE;
    if (face === 'top') return HOOD;
    if (y >= fh - 3) return y === fh - 3 ? HOOD_DARK : SKIN;
    return noise(x, y, 6) ? HOOD_LIGHT : HOOD;
  };
  cuboid(c, 40, 16, 4, 12, 4, arm);
  cuboid(c, 32, 48, 4, 12, 4, arm);
  // legs: trousers and boots
  const leg = (face, x, y, fw, fh) => {
    if (face === 'bottom') return BOOT_DARK;
    if (y >= fh - 3) return y === fh - 1 ? BOOT_DARK : BOOT;
    return noise(x, y, 7) ? PANTS_DARK : PANTS;
  };
  cuboid(c, 0, 16, 4, 12, 4, leg);
  cuboid(c, 16, 48, 4, 12, 4, leg);
  return c;
}
