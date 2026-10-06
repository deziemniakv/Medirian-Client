// The Medirian night: a cozy Halloween evening in a blocky world, drawn procedurally as pixel art.
// Layers (back to front): sky (stars, moon), far hills, forest, ground (cabin, pumpkins, fence),
// glow (warm light of windows and lanterns, animated by the UIs), fog (tileable, drifting).
import { Canvas, bayer, hash, mix } from './canvas.mjs';
import { PAL } from './palette.mjs';

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/** Dithered vertical gradient through {@code stops} ([t, colour] with t 0..1). */
function skyColor(stops, t, x, y) {
  for (let i = 1; i < stops.length; i++) {
    const [t1, c1] = stops[i];
    const [t0, c0] = stops[i - 1];
    if (t <= t1) {
      const f = (t - t0) / (t1 - t0);
      return f > bayer(x, y) ? c1 : c0;
    }
  }
  return stops[stops.length - 1][1];
}

// ------------------------------------------------------------------ sprites

export const PUMPKIN = {
  rows: [
    '....gg....',
    '...gG.....',
    '.kkokkokk.',
    'kOoOoOoOok',
    'kOyyoOyyOk',
    'kOoyoOoyOk',
    'kOoOoOoOok',
    'kOyoyyoyOk',
    'kOoyyyyoOk',
    '.kOoOoOok.',
    '..kkkkkk..'
  ],
  legend: { k: PAL.pumpkinDeep, o: PAL.pumpkinDark, O: PAL.pumpkin, y: PAL.candle, g: PAL.greenDark, G: PAL.green }
};

const BIG_PUMPKIN = {
  rows: [
    '......gg......',
    '.....gGg......',
    '..kkkokkokkk..',
    '.kOOoOOoOOoOk.',
    'kOOoOOoOOoOOok',
    'kOyyyoOoOyyyOk',
    'kOoyyoOoOyyoOk',
    'kOOoOOyOOoOOok',
    'kOOoOyyyOoOOok',
    'kOyoyyyyyyoyOk',
    'kOoyyoyyoyyoOk',
    '.kOOoOOoOOoOk.',
    '..kkkkkkkkkk..'
  ],
  legend: { k: PAL.pumpkinDeep, o: PAL.pumpkinDark, O: PAL.pumpkin, y: PAL.candle, g: PAL.greenDark, G: PAL.green }
};

const SMALL_PUMPKIN = {
  rows: [
    '..g...',
    '.kokk.',
    'kOyOyk',
    'kOoOOk',
    'kyyyyk',
    '.kkkk.'
  ],
  legend: { k: PAL.pumpkinDeep, o: PAL.pumpkinDark, O: PAL.pumpkin, y: PAL.candle, g: PAL.greenDark }
};

const BAT = {
  rows: ['.k.......k.', 'kkk.k.k.kkk', 'kkkkkkkkkkk', '.kk.kkk.kk.', '.....k.....'],
  legend: { k: PAL.ink }
};

const LANTERN = {
  rows: ['.kk.', 'kyyk', 'kYyk', 'kyyk', '.kk.'],
  legend: { k: PAL.woodDark, y: PAL.ember, Y: PAL.candle }
};

// ------------------------------------------------------------------ layers

function drawSky(c, W, H, horizon) {
  const stops = [
    [0, PAL.night0],
    [0.3, PAL.night1],
    [0.55, PAL.night2],
    [0.78, PAL.night3],
    [0.92, PAL.night4],
    [1, PAL.night5]
  ];
  for (let y = 0; y < H; y++) {
    const t = clamp(y / horizon, 0, 1);
    for (let x = 0; x < W; x++) c.set(x, y, skyColor(stops, t, x, y));
  }
}

function drawMoon(c, mx, my, r) {
  // halo: an inner ring and a dithered outer ring lightening the sky
  for (let y = my - r * 2; y <= my + r * 2; y++) {
    for (let x = mx - r * 2; x <= mx + r * 2; x++) {
      const d = Math.hypot(x - mx, y - my);
      if (d <= r) continue;
      const base = c.get(x, y) ?? PAL.night2;
      if (d <= r + 3) c.set(x, y, mix(base, PAL.night6, 0.55));
      else if (d <= r * 1.55) c.set(x, y, mix(base, PAL.night6, 0.3));
      else if (d <= r * 1.95 && bayer(x, y) < 0.5) c.set(x, y, mix(base, PAL.night6, 0.18));
    }
  }
  // disc with a soft terminator on the lower right
  for (let y = -r; y <= r; y++) {
    for (let x = -r; x <= r; x++) {
      if (x * x + y * y > r * r + r * 0.5) continue;
      const shade = (x * 0.6 + y * 0.8) / r;
      let color = PAL.moon;
      if (shade > 0.55 + bayer(mx + x, my + y) * 0.25) color = PAL.moonShade;
      if (shade > 0.9) color = PAL.moonDark;
      c.set(mx + x, my + y, color);
    }
  }
  // craters
  const craters = [[-0.35, -0.3, 0.22], [0.25, 0.1, 0.16], [-0.1, 0.42, 0.12], [0.42, -0.42, 0.1], [-0.52, 0.22, 0.09]];
  for (const [fx, fy, fr] of craters) {
    const cx = Math.round(mx + fx * r);
    const cy = Math.round(my + fy * r);
    const cr = Math.max(1, Math.round(fr * r));
    for (let y = -cr; y <= cr; y++) {
      for (let x = -cr; x <= cr; x++) {
        if (x * x + y * y > cr * cr + 0.5) continue;
        c.set(cx + x, cy + y, x + y < 0 ? PAL.moonDark : PAL.moonShade);
      }
    }
  }
}

function drawStars(c, W, horizon, moon) {
  for (let i = 0; i < W * 0.3; i++) {
    const x = Math.floor(hash(i, 1) * W);
    const y = Math.floor(hash(i, 2) * horizon * 0.85);
    if (Math.hypot(x - moon[0], y - moon[1]) < moon[2] * 2.6) continue;
    const b = hash(i, 3);
    const color = b > 0.92 ? PAL.text : b > 0.6 ? PAL.textDim : PAL.textMuted;
    if (b > 0.985) {
      // a brighter star with four rays
      c.set(x, y, PAL.white);
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.set(x + dx, y + dy, PAL.lilac);
    } else {
      c.set(x, y, color);
    }
  }
}

/** A ridge line: smooth noise quantised to blocks of {@code step} pixels (a blocky Minecraft skyline). */
function ridge(W, base, amp, step, seed) {
  const h = [];
  for (let x = 0; x < W; x += step) {
    const n = Math.sin(x * 0.011 + seed) * 0.5 + Math.sin(x * 0.029 + seed * 2.1) * 0.3 + Math.sin(x * 0.067 + seed * 3.7) * 0.2;
    const v = Math.round(base - n * amp);
    for (let i = 0; i < step && x + i < W; i++) h.push(v);
  }
  return h;
}

function drawFar(c, W, H) {
  const tops = ridge(W, H * 0.66, H * 0.07, 4, 1.3);
  for (let x = 0; x < W; x++) {
    for (let y = tops[x]; y < H; y++) {
      const edge = y === tops[x] || (y === tops[x] + 1 && bayer(x, y) > 0.5);
      c.set(x, y, edge ? PAL.night5 : PAL.night4);
    }
  }
  return tops;
}

function spruce(c, x, base, h, body, edge) {
  // a stack of widening tiers, Minecraft-spruce style
  const tiers = Math.max(2, Math.round(h / 6));
  let y = base - h;
  c.set(x, y - 1, body);
  for (let t = 0; t < tiers; t++) {
    const th = Math.ceil(h / tiers);
    for (let j = 0; j < th; j++) {
      const w = Math.round(1 + (j / th) * (2 + t * 1.6));
      for (let i = -w; i <= w; i++) c.set(x + i, y + j, i === -w ? edge : body);
    }
    y += th - 1;
  }
  c.vline(x, base - 2, base, body);
}

function deadTree(c, x, base, h, color) {
  c.vline(x, base - h, base, color);
  c.vline(x + 1, base - Math.round(h * 0.55), base, color);
  const branches = [[0.7, -1], [0.55, 1], [0.38, -1], [0.25, 1]];
  for (const [f, dir] of branches) {
    const y = base - Math.round(h * f);
    const len = Math.round(h * 0.28);
    for (let i = 1; i <= len; i++) c.set(x + dir * i + (dir > 0 ? 1 : 0), y - Math.floor(i / 2), color);
  }
}

function drawForest(c, W, H, seedShift = 0) {
  const base = Math.round(H * 0.84);
  // back row
  for (let i = 0; i < W / 6; i++) {
    const x = Math.round(i * 6 + hash(i, 11 + seedShift) * 5);
    const h = Math.round(16 + hash(i, 12 + seedShift) * 18);
    spruce(c, x, base - Math.round(hash(i, 13) * 6), h, PAL.night3, PAL.night4);
  }
  // ground under the back row
  for (let x = 0; x < W; x++) for (let y = base - 3; y < H; y++) c.set(x, y, PAL.night3);
  // front row, darker, with a few dead trees
  for (let i = 0; i < W / 11; i++) {
    const x = Math.round(i * 11 + hash(i, 21 + seedShift) * 8);
    const h = Math.round(20 + hash(i, 22 + seedShift) * 22);
    if (hash(i, 23) > 0.86) deadTree(c, x, base + 6, h, PAL.night1);
    else spruce(c, x, base + 6, h, PAL.night2, PAL.night3);
  }
  for (let x = 0; x < W; x++) for (let y = base + 4; y < H; y++) c.set(x, y, PAL.night2);
}

/** Terrain heights of the foreground, in whole blocks of 8 pixels (with a softer 4-pixel step here and there). */
function terrain(W, H) {
  const h = [];
  const ground = H - 26;
  for (let x = 0; x < W; x += 8) {
    const n = Math.sin(x * 0.018 + 0.7) * 0.6 + Math.sin(x * 0.047 + 2.3) * 0.4;
    const y = ground - Math.round(n * 1.6) * 4;
    for (let i = 0; i < 8 && x + i < W; i++) h.push(y);
  }
  return h;
}

function grassBlockColumn(c, x, top, H) {
  for (let y = top; y < H; y++) {
    const d = y - top;
    let color;
    if (d === 0) color = bayer(x, y) > 0.7 ? PAL.grassLight : PAL.grass;
    else if (d === 1) color = hash(x, y) > 0.55 ? PAL.grass : PAL.grassLight;
    else if (d === 2) color = hash(x, 7) > 0.5 ? PAL.grass : PAL.dirt;
    else {
      // deeper dirt fades into the night
      const deep = clamp((d - 3) / 14, 0, 1);
      color = deep > bayer(x, y) ? (deep > 0.75 + bayer(x + 1, y) * 0.25 ? PAL.night1 : PAL.dirtDark) : hash(x, y, 3) > 0.82 ? PAL.dirtDark : PAL.dirt;
    }
    // block seams every 8 pixels down
    if (d > 2 && d < 10 && (y - top) % 8 === 0 && hash(x, y) > 0.4) color = PAL.dirtDark;
    c.set(x, y, color);
  }
}

function cabin(c, gl, x, ground) {
  // x: left edge of the walls, ground: y of the ground under the cabin
  const w = 56;
  const wallH = 26;
  const top = ground - wallH;
  // cobblestone foundation
  for (let i = -1; i <= w; i++) {
    for (let j = 0; j < 4; j++) {
      const n = hash(x + i, ground - j, 5);
      c.set(x + i, ground - 1 - j, n > 0.7 ? PAL.stoneLight : n > 0.3 ? PAL.stone : PAL.stoneDark);
    }
  }
  // plank walls with horizontal seams
  for (let i = 0; i < w; i++) {
    for (let y = top; y < ground - 4; y++) {
      const seam = (y - top) % 4 === 3;
      const grain = hash(x + i, y, 9) > 0.86;
      c.set(x + i, y, seam ? PAL.woodDark : grain ? PAL.woodLight : PAL.wood);
    }
  }
  // log corner posts
  for (const px of [x - 1, x + w]) {
    for (let y = top - 1; y < ground - 4; y++) c.set(px, y, (y & 1) ? PAL.woodDark : '#3a2117');
    c.set(px + (px === x - 1 ? -1 : 1), top - 1, PAL.woodDark);
  }
  // roof: stepped dark-oak stairs, overhanging
  const roofBase = top - 1;
  for (let j = 0; j < 21; j++) {
    const y = roofBase - j;
    const inset = j * 1.6;
    const x0 = Math.round(x - 5 + inset);
    const x1 = Math.round(x + w + 4 - inset);
    if (x1 <= x0) break;
    for (let i = x0; i <= x1; i++) {
      const edge = i <= x0 + 1 || i >= x1 - 1;
      const course = j % 3 === 0;
      c.set(i, y, edge ? PAL.ink : course ? '#251217' : j % 3 === 1 ? '#3b1f22' : '#2f171b');
    }
  }
  // moonlit rim on the roof's left slope
  for (let j = 0; j < 21; j++) c.set(Math.round(x - 5 + j * 1.6) + 2, roofBase - j, '#5a3346');
  // chimney with smoke
  const chx = x + w - 14;
  for (let y = roofBase - 20; y < roofBase - 6; y++) {
    for (let i = 0; i < 5; i++) c.set(chx + i, y, hash(chx + i, y, 2) > 0.6 ? PAL.stoneLight : PAL.stone);
  }
  c.hline(chx - 1, chx + 5, roofBase - 20, PAL.stoneDark);
  const smoke = [[1, -23], [2, -25], [1, -27], [3, -29], [4, -31], [3, -33], [5, -35], [7, -37], [8, -39]];
  smoke.forEach(([dx, dy], i) => {
    const a = 0.5 - i * 0.045;
    c.set(chx + dx, roofBase + dy, PAL.textMuted, a);
    c.set(chx + dx + 1, roofBase + dy, PAL.textMuted, a * 0.7);
    if (i % 2) c.set(chx + dx, roofBase + dy - 1, PAL.textDim, a * 0.6);
  });
  // windows: warm light, dark mullions
  const windows = [[x + 7, top + 6], [x + w - 16, top + 6]];
  for (const [wx, wy] of windows) {
    for (let j = 0; j < 9; j++) {
      for (let i = 0; i < 9; i++) {
        const frame = i === 0 || j === 0 || i === 8 || j === 8;
        const bar = i === 4 || j === 4;
        c.set(wx + i, wy + j, frame ? PAL.woodDark : bar ? '#6b3a1c' : (i + j) % 7 === 0 ? PAL.candle : PAL.ember);
      }
    }
    halo(gl, wx + 4, wy + 4, 22, 0.55);
    // light spilling under the window
    for (let i = -2; i < 11; i++) c.set(wx + i, wy + 9, PAL.woodDark);
  }
  // door with a tiny lantern
  const dx = x + Math.floor(w / 2) - 4;
  for (let j = 0; j < 14; j++) {
    for (let i = 0; i < 8; i++) {
      const frame = i === 0 || i === 7 || j === 0;
      c.set(dx + i, ground - 5 - 13 + j, frame ? PAL.woodDark : (i === 3 ? '#3a2117' : '#5b3624'));
    }
  }
  c.set(dx + 6, ground - 11, PAL.gold);
  c.sprite(LANTERN.rows, LANTERN.legend, dx + 9, ground - 20);
  halo(gl, dx + 10, ground - 18, 14, 0.5);
  return { doorX: dx + 4 };
}

/** Warm radial light on the glow layer (dithered alpha steps). */
function halo(gl, cx, cy, r, strength) {
  for (let y = -r; y <= r; y++) {
    for (let x = -r; x <= r; x++) {
      const d = Math.hypot(x, y) / r;
      if (d >= 1) continue;
      const f = (1 - d) * (1 - d) * strength;
      // quantised to 4 levels for a pixel-art look
      const level = Math.floor(f * 4 + bayer(cx + x, cy + y) * 0.9) / 4;
      if (level <= 0) continue;
      gl.set(cx + x, cy + y, level > 0.5 ? PAL.ember : PAL.pumpkin, Math.min(0.55, level * 0.6));
    }
  }
}

function bigSpruce(c, x, ground) {
  const h = 58;
  const tiers = 7;
  let y = ground - h;
  c.vline(x, y - 2, y, PAL.leaf);
  for (let t = 0; t < tiers; t++) {
    const th = 9;
    for (let j = 0; j < th; j++) {
      const w = Math.round(1 + (j / th) * (3 + t * 1.9));
      for (let i = -w; i <= w; i++) {
        const lit = i < -w + 2 && hash(x + i, y + j) > 0.3;
        c.set(x + i, y + j, lit ? PAL.leafLight : j === th - 1 && hash(i, t) > 0.5 ? PAL.night1 : PAL.leaf);
      }
    }
    y += th - 2;
  }
  for (let j = 0; j < 6; j++) {
    c.set(x, ground - 1 - j, PAL.woodDark);
    c.set(x + 1, ground - 1 - j, PAL.wood);
  }
}

function bush(c, x, ground) {
  const rows = ['..llL..', '.lLlll.', 'lllLlll', 'dllllld'];
  c.sprite(rows, { l: PAL.leaf, L: PAL.leafLight, d: PAL.night1 }, x - 3, ground - 4);
}

function fence(c, x0, x1, ground) {
  for (let x = x0; x <= x1; x += 6) {
    c.vline(x, ground - 9, ground - 1, PAL.woodDark);
    c.vline(x + 1, ground - 9, ground - 1, PAL.wood);
  }
  c.hline(x0, x1 + 1, ground - 7, PAL.wood);
  c.hline(x0, x1 + 1, ground - 4, PAL.wood);
}

function grassTufts(c, heights, W) {
  for (let x = 2; x < W - 2; x++) {
    const n = hash(x, 77);
    const top = heights[x];
    if (n > 0.86) {
      c.set(x, top - 1, PAL.grassLight);
      if (n > 0.93) c.set(x, top - 2, PAL.grass);
    } else if (n > 0.835) {
      // a little purple flower
      c.set(x, top - 1, PAL.greenDark);
      c.set(x, top - 2, PAL.lilac);
    }
  }
}

function gravestone(c, x, ground) {
  const rows = ['.kkk.', 'kSsSk', 'kS+Sk', 'kSsSk', 'kSsSk', 'kkkkk'];
  c.sprite(rows, { k: PAL.stoneDark, S: PAL.stone, s: PAL.stoneLight, '+': PAL.stoneDark }, x, ground - 6);
}

/**
 * Builds the scene at {@code W}×{@code H} pixels. {@code focus} is where the cozy part sits:
 * 'right' (default: cabin on the right, room for a menu on the left).
 */
export function buildScene(W, H, { moon = [Math.round(W * 0.74), Math.round(H * 0.22), Math.round(H * 0.085)] } = {}) {
  const sky = new Canvas(W, H);
  const far = new Canvas(W, H);
  const forest = new Canvas(W, H);
  const ground = new Canvas(W, H);
  const glow = new Canvas(W, H);
  const horizon = Math.round(H * 0.7);

  drawSky(sky, W, H, horizon);
  drawStars(sky, W, horizon, moon);
  drawMoon(sky, moon[0], moon[1], moon[2]);
  // a bat crossing the moon and two further away
  sky.sprite(BAT.rows, BAT.legend, Math.round(moon[0] - moon[2] * 0.95), Math.round(moon[1] + moon[2] * 0.45));
  sky.sprite(['k...k', '.kkk.', '..k..'], { k: PAL.night0 }, moon[0] - moon[2] * 3, moon[1] + moon[2]);
  sky.sprite(['k...k', '.kkk.', '..k..'], { k: PAL.night0 }, moon[0] + moon[2] * 2, moon[1] - moon[2] * 1.4);

  drawFar(far, W, H);
  drawForest(forest, W, H);

  // foreground terrain
  const heights = terrain(W, H);
  const houseX = W - 92;
  // flatten the plot under the cabin and its yard
  const plot = heights[houseX + 20];
  for (let x = houseX - 30; x < Math.min(W, houseX + 70); x++) heights[x] = plot;
  for (let x = 0; x < W; x++) grassBlockColumn(ground, x, heights[x], H);
  // a gravel path from the door to the left
  const { doorX } = cabin(ground, glow, houseX, plot);
  for (let x = doorX - 40; x <= doorX; x++) {
    const y = heights[x];
    ground.set(x, y, hash(x, 31) > 0.5 ? PAL.stoneLight : PAL.stone);
    if (hash(x, 32) > 0.5) ground.set(x, y + 1, PAL.stone);
  }
  fence(ground, houseX + 62, Math.min(W - 2, houseX + 88), plot);
  bigSpruce(ground, houseX - 14, plot);
  for (const bx of [Math.round(W * 0.08), Math.round(W * 0.33), Math.round(W * 0.55), houseX - 34]) bush(ground, bx, heights[bx]);
  grassTufts(ground, heights, W);

  // pumpkins: by the door, in the yard and a lone one on the left
  const pumpkins = [[doorX - 18, 'big'], [houseX + 66, 'small'], [houseX - 26, 'small'],
    [Math.round(W * 0.17), 'huge'], [Math.round(W * 0.17) + 15, 'big'], [Math.round(W * 0.17) - 8, 'small'], [Math.round(W * 0.47), 'small']];
  for (const [px, size] of pumpkins) {
    const sprite = size === 'huge' ? BIG_PUMPKIN : size === 'big' ? PUMPKIN : SMALL_PUMPKIN;
    const top = heights[px] - sprite.rows.length;
    ground.sprite(sprite.rows, sprite.legend, px, top);
    halo(glow, px + Math.floor(sprite.rows[0].length / 2), top + Math.floor(sprite.rows.length / 2), size === 'small' ? 10 : 18, size === 'small' ? 0.35 : 0.5);
  }
  gravestone(ground, Math.round(W * 0.38), heights[Math.round(W * 0.38)]);
  gravestone(ground, Math.round(W * 0.42), heights[Math.round(W * 0.42)]);

  return { sky, far, forest, ground, glow, fog: buildFog(W, Math.round(H * 0.16)) };
}

/** Tileable fog band: soft lavender wisps with dithered transparency. */
export function buildFog(W, h) {
  const fog = new Canvas(W, h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < W; x++) {
      const t = (2 * Math.PI * x) / W;
      // separate wisps: a few broad humps along x, each thicker in its middle
      const along = Math.max(0, Math.sin(t * 3 + 0.4) * 0.6 + Math.sin(t * 7 + 2.1) * 0.4);
      const centre = 0.5 + Math.sin(t * 4 + 1.7) * 0.18;
      const vertical = clamp(1 - Math.abs(y / h - centre) * 3.2, 0, 1);
      const density = clamp(along * vertical * 1.4, 0, 1);
      if (density > bayer(x, y) * 0.8 + 0.18) fog.set(x, y, PAL.lilac, 0.08 + density * 0.18);
    }
  }
  return fog;
}

/** Where the fog band sits: over the foot of the forest. */
export const fogY = (H, fogH) => Math.round(H * 0.84) - Math.round(fogH * 0.6);

/** All layers flattened (for previews and still images). */
export function flatten(scene) {
  const { sky, far, forest, ground, glow, fog } = scene;
  const out = new Canvas(sky.width, sky.height);
  for (const layer of [sky, far, forest]) out.draw(layer, 0, 0);
  out.draw(fog, 0, fogY(sky.height, fog.height));
  out.draw(ground, 0, 0);
  out.draw(glow, 0, 0);
  return out;
}
