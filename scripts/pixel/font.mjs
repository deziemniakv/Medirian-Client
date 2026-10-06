// "Medirian Pixel" — Medirian's own bitmap font (original glyphs), used by the launcher (as a TTF)
// and for pixel lettering in generated art (logo, title screen).
//
// Glyph grid: rows 0..6 are the cap height (row 6 sits on the baseline), rows 7..8 the descender,
// rows -2..-1 hold accents of capitals. Lowercase x-height is rows 2..6. One pixel of spacing
// follows every glyph.

/** Base glyphs: 7 cap rows separated by spaces, optional '|' then 2 descender rows. */
const BASE = {
  A: '.###. #...# #...# ##### #...# #...# #...#',
  B: '####. #...# #...# ####. #...# #...# ####.',
  C: '.###. #...# #.... #.... #.... #...# .###.',
  D: '####. #...# #...# #...# #...# #...# ####.',
  E: '##### #.... #.... ####. #.... #.... #####',
  F: '##### #.... #.... ####. #.... #.... #....',
  G: '.#### #.... #.... #..## #...# #...# .####',
  H: '#...# #...# #...# ##### #...# #...# #...#',
  I: '### .#. .#. .#. .#. .#. ###',
  J: '....# ....# ....# ....# #...# #...# .###.',
  K: '#...# #..#. #.#.. ##... #.#.. #..#. #...#',
  L: '#.... #.... #.... #.... #.... #.... #####',
  M: '#...# ##.## #.#.# #.#.# #...# #...# #...#',
  N: '#...# ##..# #.#.# #..## #...# #...# #...#',
  O: '.###. #...# #...# #...# #...# #...# .###.',
  P: '####. #...# #...# ####. #.... #.... #....',
  Q: '.###. #...# #...# #...# #.#.# #..#. .##.#',
  R: '####. #...# #...# ####. #.#.. #..#. #...#',
  S: '.#### #.... #.... .###. ....# ....# ####.',
  T: '##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..',
  U: '#...# #...# #...# #...# #...# #...# .###.',
  V: '#...# #...# #...# #...# .#.#. .#.#. ..#..',
  W: '#...# #...# #...# #.#.# #.#.# ##.## #...#',
  X: '#...# #...# .#.#. ..#.. .#.#. #...# #...#',
  Y: '#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..',
  Z: '##### ....# ...#. ..#.. .#... #.... #####',

  a: '..... ..... .###. ....# .#### #...# .####',
  b: '#.... #.... #.##. ##..# #...# #...# ####.',
  c: '..... ..... .###. #.... #.... #.... .###.',
  d: '....# ....# .##.# #..## #...# #...# .####',
  e: '..... ..... .###. #...# ##### #.... .####',
  f: '..## .#.. #### .#.. .#.. .#.. .#..',
  g: '..... ..... .#### #...# #...# #...# .#### | ....# ####.',
  h: '#.... #.... #.##. ##..# #...# #...# #...#',
  i: '# . # # # # #',
  j: '...# .... ...# ...# ...# ...# ...# | #..# .##.',
  k: '#... #... #..# #.#. ##.. #.#. #..#',
  l: '#. #. #. #. #. #. .#',
  m: '..... ..... ##.#. #.#.# #.#.# #...# #...#',
  n: '..... ..... ####. #...# #...# #...# #...#',
  o: '..... ..... .###. #...# #...# #...# .###.',
  p: '..... ..... #.##. ##..# #...# #...# ####. | #.... #....',
  q: '..... ..... .##.# #..## #...# #...# .#### | ....# ....#',
  r: '..... ..... #.##. ##..# #.... #.... #....',
  s: '..... ..... .#### #.... .###. ....# ####.',
  t: '.#. .#. ### .#. .#. .#. ..#',
  u: '..... ..... #...# #...# #...# #...# .####',
  v: '..... ..... #...# #...# #...# .#.#. ..#..',
  w: '..... ..... #...# #...# #.#.# #.#.# .####',
  x: '..... ..... #...# .#.#. ..#.. .#.#. #...#',
  y: '..... ..... #...# #...# #...# #...# .#### | ....# ####.',
  z: '..... ..... ##### ...#. ..#.. .#... #####',
  ı: '. . # # # # #',

  0: '.###. #...# #..## #.#.# ##..# #...# .###.',
  1: '..#.. .##.. ..#.. ..#.. ..#.. ..#.. #####',
  2: '.###. #...# ....# ..##. .#... #.... #####',
  3: '.###. #...# ....# ..##. ....# #...# .###.',
  4: '...#. ..##. .#.#. #..#. ##### ...#. ...#.',
  5: '##### #.... ####. ....# ....# #...# .###.',
  6: '..##. .#... #.... ####. #...# #...# .###.',
  7: '##### #...# ....# ...#. ..#.. ..#.. ..#..',
  8: '.###. #...# #...# .###. #...# #...# .###.',
  9: '.###. #...# #...# .#### ....# ...#. .##..',

  '!': '# # # # # . #',
  '"': '#.# #.# ... ... ... ... ...',
  '#': '.#.#. .#.#. ##### .#.#. ##### .#.#. .#.#.',
  $: '..#.. .#### #.#.. .###. ..#.# ####. ..#..',
  '%': '#...# #..#. ...#. ..#.. .#... .#..# #...#',
  '&': '.##.. #..#. .##.. .#... #.#.# #..#. .##.#',
  "'": '# # . . . . .',
  '(': '..# .#. #.. #.. #.. .#. ..#',
  ')': '#.. .#. ..# ..# ..# .#. #..',
  '*': '..... ..#.. #.#.# .###. #.#.# ..#.. .....',
  '+': '..... ..#.. ..#.. ##### ..#.. ..#.. .....',
  ',': '. . . . . # # | # .',
  '-': '..... ..... ..... ##### ..... ..... .....',
  '.': '. . . . . . #',
  '/': '....# ...#. ...#. ..#.. .#... .#... #....',
  ':': '. . # . . . #',
  ';': '. . # . . . # | # .',
  '<': '...# ..#. .#.. #... .#.. ..#. ...#',
  '=': '..... ..... ##### ..... ##### ..... .....',
  '>': '#... .#.. ..#. ...# ..#. .#.. #...',
  '?': '.###. #...# ....# ...#. ..#.. ..... ..#..',
  '@': '.###. #...# #.### #.#.# #.### #.... .####',
  '[': '### #.. #.. #.. #.. #.. ###',
  '\\': '#.... .#... .#... ..#.. ...#. ...#. ....#',
  ']': '### ..# ..# ..# ..# ..# ###',
  '^': '..#.. .#.#. #...# ..... ..... ..... .....',
  _: '..... ..... ..... ..... ..... ..... ..... | #####  .....',
  '`': '#. .# .. .. .. .. ..',
  '{': '..## .#.. .#.. #... .#.. .#.. ..##',
  '|': '# # # # # # # | # .',
  '}': '##.. ..#. ..#. ...# ..#. ..#. ##..',
  '~': '...... ...... .##..# #..##. ...... ...... ......',

  ß: '.##.. #..#. #.#.. #..#. #...# #...# #.##.',
  ł: '.#. .#. .## ##. .#. .#. ..#',
  Ł: '.#... .#... .##.. ##... .#... .#... .####',
  '¿': '..#.. ..... ..#.. .#... #.... #...# .###.',
  '¡': '# . # # # # #',
  '…': '..... ..... ..... ..... ..... ..... #.#.#',
  '—': '....... ....... ....... ####### ....... ....... .......',
  '–': '..... ..... ..... ##### ..... ..... .....',
  '→': '....... ....#.. .....#. ####### .....#. ....#.. .......',
  '←': '....... ..#.... .#..... ####### .#..... ..#.... .......',
  '↑': '..#.. .###. #.#.# ..#.. ..#.. ..#.. .....',
  '↓': '..... ..#.. ..#.. ..#.. #.#.# .###. ..#..',
  '·': '. . . # . . .',
  '•': '... ... .#. ### .#. ... ...',
  '×': '..... #...# .#.#. ..#.. .#.#. #...# .....',
  '°': '.#. #.# .#. ... ... ... ...',
  '«': '...... ..#..# .#..#. #..#.. .#..#. ..#..# ......',
  '»': '...... #..#.. .#..#. ..#..# .#..#. #..#.. ......',
  '„': '... ... ... ... ... #.# #.# | #.# ...',
  '“': '#.# #.# ... ... ... ... ...',
  '”': '#.# #.# ... ... ... ... ...',
  '‘': '# # . . . . .',
  '’': '# # . . . . .',
  '©': '.#####. #.....# #.###.# #.#...# #.###.# #.....# .#####.',
  '♥': '.#.#. ##### ##### .###. ..#.. ..... .....'
};

/** Accented letters: base letter + mark. */
const ACCENTED = {
  á: ['a', 'acute'], é: ['e', 'acute'], í: ['ı', 'acute'], ó: ['o', 'acute'], ú: ['u', 'acute'],
  ć: ['c', 'acute'], ń: ['n', 'acute'], ś: ['s', 'acute'], ź: ['z', 'acute'],
  Á: ['A', 'acute'], É: ['E', 'acute'], Í: ['I', 'acute'], Ó: ['O', 'acute'], Ú: ['U', 'acute'],
  Ć: ['C', 'acute'], Ń: ['N', 'acute'], Ś: ['S', 'acute'], Ź: ['Z', 'acute'],
  ą: ['a', 'ogonek'], ę: ['e', 'ogonek'], Ą: ['A', 'ogonek'], Ę: ['E', 'ogonek'],
  ż: ['z', 'dot'], Ż: ['Z', 'dot'],
  ä: ['a', 'diaeresis'], ö: ['o', 'diaeresis'], ü: ['u', 'diaeresis'],
  Ä: ['A', 'diaeresis'], Ö: ['O', 'diaeresis'], Ü: ['U', 'diaeresis'],
  ñ: ['n', 'tilde'], Ñ: ['N', 'tilde']
};

/** A glyph: width and a set of "x,row" pixels. */
function parse(spec) {
  const [cap, desc] = spec.split('|').map((s) => s.trim().split(/\s+/).filter(Boolean));
  const rows = [...cap, ...(desc ?? [])];
  const width = Math.max(...rows.map((r) => r.length));
  const pixels = new Set();
  rows.forEach((row, r) => [...row].forEach((ch, x) => ch === '#' && pixels.add(`${x},${r}`)));
  return { width, pixels };
}

function accent(base, mark, upper) {
  const pixels = new Set(base.pixels);
  const w = base.width;
  const c = Math.floor(w / 2);
  const top = upper ? -2 : 0;
  const put = (x, r) => pixels.add(`${x},${r}`);
  switch (mark) {
    case 'acute':
      put(Math.min(w - 1, c + 1), top);
      put(c, top + 1);
      break;
    case 'dot':
      put(c, top);
      break;
    case 'diaeresis':
      put(c - 1, top);
      put(c + 1, top);
      break;
    case 'tilde':
      put(c - 1, top);
      put(c, top);
      put(c + 2 > w - 1 ? w - 1 : c + 2, top);
      put(c - 2 < 0 ? 0 : c - 2, top + 1);
      put(c + 1, top + 1);
      break;
    case 'ogonek':
      put(w - 2, 7);
      put(w - 1, 8);
      break;
    default:
      throw new Error(mark);
  }
  return { width: w, pixels };
}

export const GLYPHS = new Map();
for (const [ch, spec] of Object.entries(BASE)) GLYPHS.set(ch, parse(spec));
for (const [ch, [base, mark]] of Object.entries(ACCENTED)) {
  GLYPHS.set(ch, accent(GLYPHS.get(base), mark, ch === ch.toUpperCase() && ch !== ch.toLowerCase()));
}
GLYPHS.delete('ı');
export const SPACE_WIDTH = 3;

/** Width of {@code text} in font pixels (with 1 px spacing between glyphs, none after the last). */
export function textWidth(text) {
  let w = 0;
  for (const ch of text) w += (ch === ' ' ? SPACE_WIDTH : (GLYPHS.get(ch) ?? GLYPHS.get('?')).width) + 1;
  return Math.max(0, w - 1);
}

/**
 * Calls {@code plot(x, y)} for every pixel of {@code text}; (x, y) is the glyph's top-left at cap
 * height (accents of capitals go to y - 2, descenders to y + 7..8).
 */
export function layout(text, plot) {
  let x = 0;
  for (const ch of text) {
    if (ch === ' ') {
      x += SPACE_WIDTH + 1;
      continue;
    }
    const glyph = GLYPHS.get(ch) ?? GLYPHS.get('?');
    for (const key of glyph.pixels) {
      const [gx, gr] = key.split(',').map(Number);
      plot(x + gx, gr);
    }
    x += glyph.width + 1;
  }
  return x - 1;
}

// ------------------------------------------------------------------ TrueType export

const UNIT = 128; // font units per pixel
const UPM = UNIT * 8;
const ASCENT = UNIT * 9; // rows -2..6 above the baseline
const DESCENT = UNIT * 2;

/** Traces the outline of a glyph's pixels into clockwise contours (holes come out counter-clockwise). */
function contours(pixels) {
  const filled = (x, r) => pixels.has(`${x},${r}`);
  // lattice: x to the right, Y = 6 - r upwards (pixel occupies [x, x+1] × [Y, Y+1])
  const edges = new Map();
  const add = (x0, y0, x1, y1) => {
    const key = `${x0},${y0}`;
    if (!edges.has(key)) edges.set(key, []);
    edges.get(key).push([x1, y1]);
  };
  for (const key of pixels) {
    const [x, r] = key.split(',').map(Number);
    const Y = 6 - r;
    if (!filled(x - 1, r)) add(x, Y, x, Y + 1); // left edge goes up
    if (!filled(x, r - 1)) add(x, Y + 1, x + 1, Y + 1); // top edge goes right
    if (!filled(x + 1, r)) add(x + 1, Y + 1, x + 1, Y); // right edge goes down
    if (!filled(x, r + 1)) add(x + 1, Y, x, Y); // bottom edge goes left
  }
  const result = [];
  for (;;) {
    const startKey = [...edges.keys()].find((k) => edges.get(k).length > 0);
    if (!startKey) break;
    let [cx, cy] = startKey.split(',').map(Number);
    const points = [[cx, cy]];
    let dir = null;
    for (;;) {
      const out = edges.get(`${cx},${cy}`);
      let index = 0;
      if (out.length > 1 && dir) {
        // two pixels touching at a corner: turn right so they stay separate contours
        const right = [dir[1], -dir[0]];
        index = Math.max(0, out.findIndex(([nx, ny]) => nx - cx === right[0] && ny - cy === right[1]));
      }
      const [nx, ny] = out.splice(index, 1)[0];
      dir = [Math.sign(nx - cx), Math.sign(ny - cy)];
      cx = nx;
      cy = ny;
      if (cx === points[0][0] && cy === points[0][1]) break;
      points.push([cx, cy]);
    }
    // drop collinear points
    const simple = points.filter((p, i) => {
      const a = points[(i - 1 + points.length) % points.length];
      const b = points[(i + 1) % points.length];
      return (p[0] - a[0]) * (b[1] - p[1]) - (p[1] - a[1]) * (b[0] - p[0]) !== 0;
    });
    result.push(simple.map(([x, y]) => [x * UNIT, y * UNIT]));
  }
  return result;
}

class Writer {
  constructor() {
    this.parts = [];
    this.length = 0;
  }
  u8(v) { return this.push(Buffer.from([v & 0xff])); }
  u16(v) { const b = Buffer.alloc(2); b.writeUInt16BE(v & 0xffff); return this.push(b); }
  i16(v) { const b = Buffer.alloc(2); b.writeInt16BE(v); return this.push(b); }
  u32(v) { const b = Buffer.alloc(4); b.writeUInt32BE(v >>> 0); return this.push(b); }
  tag(s) { return this.push(Buffer.from(s, 'latin1')); }
  push(b) { this.parts.push(b); this.length += b.length; return this; }
  buffer() { return Buffer.concat(this.parts); }
}

const checksum = (buf) => {
  const padded = Buffer.concat([buf, Buffer.alloc((4 - (buf.length % 4)) % 4)]);
  let sum = 0;
  for (let i = 0; i < padded.length; i += 4) sum = (sum + padded.readUInt32BE(i)) >>> 0;
  return sum;
};

/** Builds "Medirian Pixel" as a TrueType font. */
export function buildTtf() {
  const chars = [...GLYPHS.keys()].sort((a, b) => a.codePointAt(0) - b.codePointAt(0));
  // glyph 0 .notdef, glyph 1 space, then the characters
  const glyphs = [
    { advance: 6 * UNIT, contours: [[[UNIT, 0], [UNIT, 7 * UNIT], [5 * UNIT, 7 * UNIT], [5 * UNIT, 0]], [[2 * UNIT, UNIT], [4 * UNIT, UNIT], [4 * UNIT, 6 * UNIT], [2 * UNIT, 6 * UNIT]]] },
    { advance: (SPACE_WIDTH + 1) * UNIT, contours: [], code: 0x20 }
  ];
  for (const ch of chars) {
    const g = GLYPHS.get(ch);
    glyphs.push({ advance: (g.width + 1) * UNIT, contours: contours(g.pixels), code: ch.codePointAt(0) });
  }
  glyphs.push({ advance: (SPACE_WIDTH + 1) * UNIT, contours: [], code: 0xa0 });

  // glyf + loca
  const glyf = new Writer();
  const loca = [];
  let maxPoints = 0;
  let maxContours = 0;
  let xMin = 0, yMin = 0, xMax = 0, yMax = 0;
  for (const g of glyphs) {
    loca.push(glyf.length);
    g.lsb = 0;
    if (g.contours.length === 0) continue;
    const all = g.contours.flat();
    const gx0 = Math.min(...all.map((p) => p[0]));
    const gy0 = Math.min(...all.map((p) => p[1]));
    const gx1 = Math.max(...all.map((p) => p[0]));
    const gy1 = Math.max(...all.map((p) => p[1]));
    g.lsb = gx0;
    g.xMax = gx1;
    xMin = Math.min(xMin, gx0); yMin = Math.min(yMin, gy0); xMax = Math.max(xMax, gx1); yMax = Math.max(yMax, gy1);
    maxPoints = Math.max(maxPoints, all.length);
    maxContours = Math.max(maxContours, g.contours.length);
    glyf.i16(g.contours.length).i16(gx0).i16(gy0).i16(gx1).i16(gy1);
    let end = -1;
    for (const c of g.contours) {
      end += c.length;
      glyf.u16(end);
    }
    glyf.u16(0); // no instructions
    for (let i = 0; i < all.length; i++) glyf.u8(0x01); // on-curve, 16-bit coordinates
    let px = 0;
    for (const [x] of all) { glyf.i16(x - px); px = x; }
    let py = 0;
    for (const [, y] of all) { glyf.i16(y - py); py = y; }
    while (glyf.length % 4) glyf.u8(0);
  }
  loca.push(glyf.length);
  const locaW = new Writer();
  for (const off of loca) locaW.u32(off);

  const numGlyphs = glyphs.length;
  const advanceMax = Math.max(...glyphs.map((g) => g.advance));

  const head = new Writer()
    .u32(0x00010000).u32(0x00010000) // version, fontRevision 1.0
    .u32(0) // checkSumAdjustment (patched below)
    .u32(0x5f0f3cf5).u16(0x000b).u16(UPM)
    .u32(0).u32(0).u32(0).u32(0) // created, modified
    .i16(xMin).i16(yMin).i16(xMax).i16(yMax)
    .u16(0).u16(8).i16(2).i16(1).i16(0); // macStyle, lowestRecPPEM, fontDirectionHint, indexToLocFormat long, glyphDataFormat

  const hhea = new Writer()
    .u32(0x00010000).i16(ASCENT).i16(-DESCENT).i16(0).u16(advanceMax)
    .i16(0).i16(Math.min(...glyphs.map((g) => g.advance - (g.xMax ?? 0)))).i16(xMax)
    .i16(1).i16(0).i16(0).i16(0).i16(0).i16(0).i16(0).i16(0).u16(numGlyphs);

  const hmtx = new Writer();
  for (const g of glyphs) hmtx.u16(g.advance).i16(g.lsb);

  const maxp = new Writer().u32(0x00010000).u16(numGlyphs).u16(maxPoints).u16(maxContours)
    .u16(0).u16(0).u16(2).u16(0).u16(0).u16(0).u16(0).u16(0).u16(0).u16(0).u16(0);

  // cmap format 4, one segment per character
  const mapped = glyphs.map((g, i) => [g.code, i]).filter(([c]) => c !== undefined && c <= 0xffff).sort((a, b) => a[0] - b[0]);
  const segs = [...mapped.map(([c, gid]) => ({ start: c, end: c, delta: (gid - c) & 0xffff })), { start: 0xffff, end: 0xffff, delta: 1 }];
  const segCount = segs.length;
  const searchRange = 2 * 2 ** Math.floor(Math.log2(segCount));
  const sub = new Writer().u16(4).u16(16 + 8 * segCount).u16(0).u16(segCount * 2).u16(searchRange)
    .u16(Math.log2(searchRange / 2)).u16(segCount * 2 - searchRange);
  for (const s of segs) sub.u16(s.end);
  sub.u16(0);
  for (const s of segs) sub.u16(s.start);
  for (const s of segs) sub.u16(s.delta);
  for (let i = 0; i < segCount; i++) sub.u16(0);
  const cmap = new Writer().u16(0).u16(1).u16(3).u16(1).u32(12).push(sub.buffer());

  const strings = {
    0: 'Copyright 2026 Medirian Client',
    1: 'Medirian Pixel',
    2: 'Regular',
    3: 'Medirian Pixel Regular 1.000',
    4: 'Medirian Pixel',
    5: 'Version 1.000',
    6: 'MedirianPixel-Regular'
  };
  const name = new Writer();
  const ids = Object.keys(strings).map(Number);
  name.u16(0).u16(ids.length).u16(6 + 12 * ids.length);
  const storage = [];
  let offset = 0;
  for (const id of ids) {
    const bytes = Buffer.from(strings[id], 'utf16le').swap16();
    name.u16(3).u16(1).u16(0x0409).u16(id).u16(bytes.length).u16(offset);
    storage.push(bytes);
    offset += bytes.length;
  }
  name.push(Buffer.concat(storage));

  const codes = mapped.map(([c]) => c);
  const avg = Math.round(glyphs.slice(1).reduce((s, g) => s + g.advance, 0) / (numGlyphs - 1));
  const os2 = new Writer()
    .u16(4).i16(avg).u16(400).u16(5).u16(0)
    .i16(UNIT * 4).i16(UNIT * 4).i16(0).i16(UNIT).i16(UNIT * 4).i16(UNIT * 4).i16(0).i16(UNIT * 4)
    .i16(UNIT).i16(UNIT * 3).i16(0)
    .push(Buffer.from([2, 0, 5, 9, 0, 0, 0, 0, 0, 0])) // panose: text, monospaced-ish pixel face
    .u32(0x80000007).u32(0x20).u32(0).u32(0) // Basic Latin, Latin-1, Latin Extended-A, punctuation, arrows
    .tag('MDRN').u16(0x00c0).u16(Math.min(...codes)).u16(Math.min(0xffff, Math.max(...codes)))
    .i16(ASCENT).i16(-DESCENT).i16(0).u16(ASCENT).u16(DESCENT)
    .u32(0b11).u32(0) // code pages: Latin 1, Latin 2
    .i16(UNIT * 5).i16(UNIT * 7).u16(0).u16(0x20).u16(1);

  const post = new Writer().u32(0x00030000).u32(0).i16(-UNIT).i16(UNIT).u32(0).u32(0).u32(0).u32(0).u32(0);

  const tables = {
    'OS/2': os2.buffer(), cmap: cmap.buffer(), glyf: glyf.buffer(), head: head.buffer(), hhea: hhea.buffer(),
    hmtx: hmtx.buffer(), loca: locaW.buffer(), maxp: maxp.buffer(), name: name.buffer(), post: post.buffer()
  };
  const tags = Object.keys(tables).sort();
  const numTables = tags.length;
  const entrySelector = Math.floor(Math.log2(numTables));
  const dir = new Writer().u32(0x00010000).u16(numTables).u16(16 * 2 ** entrySelector).u16(entrySelector)
    .u16(numTables * 16 - 16 * 2 ** entrySelector);
  let at = 12 + 16 * numTables;
  const body = [];
  for (const tag of tags) {
    const data = tables[tag];
    dir.tag(tag).u32(checksum(data)).u32(at).u32(data.length);
    const padded = Buffer.concat([data, Buffer.alloc((4 - (data.length % 4)) % 4)]);
    body.push(padded);
    at += padded.length;
  }
  const font = Buffer.concat([dir.buffer(), ...body]);
  // head.checkSumAdjustment
  const headOffset = 12 + 16 * numTables + body.slice(0, tags.indexOf('head')).reduce((s, b) => s + b.length, 0);
  font.writeUInt32BE((0xb1b0afba - checksum(font)) >>> 0, headOffset + 8);
  return font;
}
