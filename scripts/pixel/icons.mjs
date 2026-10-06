// Medirian's 16×16 pixel icons: one per module, per category, and the menu / UI icons.
// Drawn with a few primitives, then outlined in ink so every icon reads on dark tiles.
import { Canvas } from './canvas.mjs';
import { PAL } from './palette.mjs';

const C = {
  k: PAL.ink,
  o: PAL.pumpkin, O: PAL.pumpkinLight, d: PAL.pumpkinDark, D: PAL.pumpkinDeep,
  y: PAL.candle, Y: PAL.gold, e: PAL.ember,
  p: PAL.amethyst, P: PAL.lilac, v: PAL.violet, V: PAL.purple, L: PAL.lavender,
  w: PAL.text, W: '#ffffff',
  s: '#b8b0c8', S: '#7c7590', m: PAL.textMuted, M: PAL.stone, Z: PAL.stoneDark,
  r: PAL.red, R: PAL.redDark, g: PAL.green, G: PAL.greenDark,
  b: '#79b2e0', B: '#3f5f94', c: '#86dcd2',
  n: PAL.woodLight, N: PAL.wood, t: '#8a6a4a'
};

/** Tiny 3×5 digits/letters for labels inside icons. */
const MINI = {
  0: '### #.# #.# #.# ###', 1: '.#. ##. .#. .#. ###', 2: '### ..# ### #.. ###', 3: '### ..# ### ..# ###',
  6: '### #.. ### #.# ###', F: '### #.. ##. #.. #..', P: '### #.# ### #.. #..', S: '### #.. ### ..# ###',
  G: '### #.. #.# #.# ###', X: '#.# #.# .#. #.# #.#', Y: '#.# #.# .#. .#. .#.', Z: '### ..# .#. #.. ###',
  z: '... ### .#. #.. ###'
};

class Icon extends Canvas {
  constructor() {
    super(16, 16);
  }
  px(x, y, c) { this.set(x, y, C[c] ?? c); return this; }
  box(x, y, w, h, c) { this.rect(x, y, w, h, C[c] ?? c); return this; }
  ln(x0, y0, x1, y1, c) { this.line(x0, y0, x1, y1, C[c] ?? c); return this; }
  dot(cx, cy, r, c) {
    if (c === null) {
      for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r + r * 0.6) this.clear(cx + x, cy + y);
    } else {
      this.disc(cx, cy, r, C[c] ?? c);
    }
    return this;
  }
  /** Character map at (x, y) using the icon colours. */
  map(x, y, rows) { this.sprite(rows, C, x, y); return this; }
  mini(x, y, text, c) {
    let at = x;
    for (const ch of text) {
      MINI[ch].split(' ').forEach((row, j) => [...row].forEach((v, i) => v === '#' && this.px(at + i, y + j, c)));
      at += 4;
    }
    return this;
  }
  ring(cx, cy, r, c) {
    for (let a = 0; a < 360; a += 2) this.px(Math.round(cx + Math.cos((a * Math.PI) / 180) * r), Math.round(cy + Math.sin((a * Math.PI) / 180) * r), c);
    return this;
  }
}

// shared shapes
const heart = (i, x, y) => i.map(x, y, ['.rr.rr.', 'rWrrrrr', 'rrrrrrr', '.rrrrr.', '..rrr..', '...r...']);
const boot = (i, x, y) => i.map(x, y, ['.NNN....', '.NnN....', '.NnN....', '.NnNN...', '.NnnNNN.', 'NnnnnnnN', 'NNNNNNNN', 'tttttttt']);
const eye = (i, x, y, c = 'p') => i.map(x, y, [
  '...wwwwww...', '.wwwwwwwwww.', `wwww${c}${c}${c}${c}wwww`, `www${c}${c}kk${c}${c}www`, `www${c}${c}kk${c}${c}www`, `wwww${c}${c}${c}${c}wwww`, '.wwwwwwwwww.', '...wwwwww...'
]);

const DRAW = {
  // ---------------------------------------------------------------- modules
  combo(i) {
    for (const [dx, dy] of [[0, -6], [0, 6], [-6, 0], [6, 0], [-4, -4], [4, 4], [-4, 4], [4, -4]]) i.ln(7, 7, 7 + dx, 7 + dy, 'y');
    i.dot(7, 7, 3, 'o').dot(7, 7, 1, 'y').px(6, 6, 'W');
    i.box(10, 10, 5, 6, 'k').mini(11, 11, '3', 'W');
  },
  cps(i) {
    i.map(4, 1, ['...S....', '...S....', '.oooSss.', 'ooOoSsss', 'ooooSsss', 'ooooSsss', 'SSSSSSSS', 'ssssssss', 'ssssssss', 'ssssssss', 'ssssssss', '.ssssss.', '..SSSS..']);
  },
  healthtags(i) {
    i.box(1, 2, 14, 7, 'M').box(2, 3, 12, 5, 'Z').box(3, 4, 7, 1, 'w').box(3, 6, 5, 1, 's').px(7, 9, 'M').px(8, 9, 'M');
    heart(i, 8, 9);
  },
  hitcolor(i) {
    i.dot(6, 9, 5, 'R').dot(6, 9, 3, 'r');
    i.map(3, 1, ['..........ss', '.........sWs', '........sWs.', '.......sWs..', '......sWs...', '..Y..sWs....', '..YYsWs.....', '...YYs......', '..nYYY......', '.nn..Y......', 'nn..........', 'n...........']);
  },
  reach(i) {
    i.map(0, 3, ['..w.........w...', '.ww.........ww..', 'wwwwwwwwwwwwwww.', '.ww.........ww..', '..w.........w...']);
    i.box(1, 10, 14, 4, 'Y').box(1, 13, 14, 1, 'd');
    for (const x of [2, 4, 6, 8, 10, 12]) i.px(x, 10, 'D').px(x, 11, x % 4 === 2 ? 'D' : 'Y');
  },
  targethud(i) {
    i.box(0, 3, 16, 10, 'M').box(1, 4, 14, 8, 'Z');
    i.map(2, 5, ['nnnnn', 'nkntk'.replace('t', 'n'), 'nnnnn', 'nkkkn', 'nnnnn']);
    i.box(8, 6, 6, 2, 'r').box(12, 6, 2, 2, 'R').box(8, 9, 5, 1, 'o');
  },
  clock(i) {
    i.dot(8, 8, 7, 'M').dot(8, 8, 6, 'w').dot(8, 8, 5, 'L');
    for (const [x, y] of [[8, 3], [13, 8], [8, 13], [3, 8]]) i.px(x, y, 'V');
    i.ln(8, 8, 8, 4, 'k').ln(8, 8, 11, 8, 'k').px(8, 8, 'o');
  },
  fps(i) {
    i.box(0, 2, 16, 11, 's').box(1, 3, 14, 9, 'V').box(1, 3, 14, 1, 'v');
    i.mini(2, 5, 'FPS', 'O');
    i.box(6, 13, 4, 1, 'S').box(4, 14, 8, 2, 's');
  },
  keystrokes(i) {
    const key = (x, y, down) => i.box(x, y, 4, 4, down ? 'o' : 'w').box(x, y + 3, 4, 1, down ? 'd' : 's').px(x, y, down ? 'O' : 'W');
    key(6, 2, true);
    key(1, 8, false);
    key(6, 8, false);
    key(11, 8, false);
  },
  ping(i) {
    i.box(1, 11, 3, 4, 'g').box(5, 8, 3, 7, 'g').box(9, 5, 3, 10, 'g').box(13, 2, 2, 13, 'M');
    for (const x of [1, 5, 9]) i.px(x, [11, 8, 5][(x - 1) / 4], 'W');
  },
  sessioninfo(i) {
    i.box(3, 1, 10, 2, 'n').box(3, 13, 10, 2, 'n').box(3, 2, 10, 1, 'N').box(3, 13, 10, 1, 'N');
    i.map(4, 3, ['PYYYYYYP', '.PYYYYP.', '..PYYP..', '...PP...', '...PY...', '..P.YP..', '.P..Y.P.', 'P..YYY.P', 'PYYYYYYP', 'PPPPPPPP'].slice(0, 10));
  },
  speed(i) {
    for (let a = 180; a <= 360; a += 3) {
      const r = (a * Math.PI) / 180;
      i.px(Math.round(8 + Math.cos(r) * 7), Math.round(11 + Math.sin(r) * 7), 'M');
      i.px(Math.round(8 + Math.cos(r) * 6), Math.round(11 + Math.sin(r) * 6), a > 300 ? 'r' : a > 240 ? 'o' : 'g');
    }
    i.box(1, 11, 15, 2, 'M').ln(8, 11, 12, 6, 'w').dot(8, 11, 1, 'O');
  },
  stopwatch(i) {
    i.box(6, 0, 4, 2, 's').box(7, 2, 2, 1, 'S').px(13, 3, 's').px(12, 4, 's');
    i.dot(8, 9, 6, 'S').dot(8, 9, 5, 'w').ln(8, 9, 8, 5, 'r').ln(8, 9, 11, 11, 'k').px(8, 9, 'o');
  },
  autogg(i) {
    i.box(0, 1, 16, 11, 'w').box(1, 0, 14, 13, 'w').map(2, 13, ['ww', 'w.']).px(0, 1, 'w');
    i.box(1, 11, 14, 1, 's');
    i.mini(3, 4, 'GG', 'v');
  },
  chat(i) {
    i.box(0, 1, 13, 9, 'P').box(1, 0, 11, 11, 'P').map(2, 11, ['PP', 'P.']);
    i.box(2, 3, 9, 1, 'V').box(2, 5, 7, 1, 'V').box(2, 7, 8, 1, 'V');
    i.box(9, 8, 7, 6, 'w').box(10, 7, 5, 8, 'w').px(14, 15, 'w').box(10, 10, 5, 1, 's').box(10, 12, 3, 1, 's');
  },
  screenshot(i) {
    i.box(4, 2, 5, 2, 'M').box(0, 4, 16, 10, 'M').box(1, 5, 14, 8, 'Z');
    i.dot(8, 9, 4, 's').dot(8, 9, 3, 'B').dot(8, 9, 1, 'b').px(7, 8, 'W');
    i.box(12, 5, 2, 1, 'y');
  },
  serverinfo(i) {
    for (const y of [1, 6, 11]) {
      i.box(1, y, 14, 4, 's').box(1, y + 3, 14, 1, 'S');
      i.px(3, y + 1, y === 6 ? 'o' : 'g').px(5, y + 1, 'g').box(8, y + 1, 5, 1, 'S');
    }
  },
  freelook(i) {
    eye(i, 2, 5);
    i.map(1, 0, ['...OOOOOOOO...', '..O........O..', '.O..........O.', 'O.............']);
    i.map(12, 1, ['.O.', 'OOO', '.O.']).px(0, 4, 'O');
  },
  togglesneak(i) {
    boot(i, 1, 8);
    i.box(11, 1, 3, 7, 'P').map(9, 7, ['PPPPPPP', '.PPPPP.', '..PPP..', '...P...']);
  },
  togglesprint(i) {
    boot(i, 6, 7);
    i.box(0, 8, 5, 1, 'w').box(1, 10, 4, 1, 'w').box(0, 12, 5, 1, 'w');
    i.map(9, 0, ['..YY', '.YY.', 'YYYY', '..Y.', '.Y..']);
  },
  zoom(i) {
    i.ln(2, 13, 9, 6, 'Y').ln(3, 13, 10, 6, 'd').ln(2, 12, 9, 5, 'Y').ln(1, 14, 3, 12, 'D').ln(2, 14, 3, 13, 'D');
    i.dot(11, 4, 3, 'Y').dot(11, 4, 2, 'b').px(10, 3, 'W');
  },
  dynamicfps(i) {
    i.dot(7, 8, 6, 'y').dot(10, 6, 5, 'k');
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (i.get(x, y) === PAL.ink) i.clear(x, y);
    i.px(3, 7, 'W').px(4, 12, 'Y').px(6, 13, 'Y');
    i.mini(10, 0, 'z', 'P').mini(12, 6, 'z', 'L');
  },
  entityculling(i) {
    eye(i, 2, 4, 'p');
    i.ln(2, 14, 14, 2, 'r').ln(3, 14, 15, 2, 'r').ln(2, 13, 14, 1, 'R');
  },
  fpsgraph(i) {
    i.box(1, 1, 1, 14, 'w').box(1, 14, 14, 1, 'w');
    const pts = [[3, 11], [5, 8], [7, 9], [9, 5], [11, 6], [14, 2]];
    for (let k = 1; k < pts.length; k++) i.ln(pts[k - 1][0], pts[k - 1][1], pts[k][0], pts[k][1], 'g');
    for (const [x, y] of pts) i.px(x, y, 'O');
  },
  memory(i) {
    i.box(0, 4, 16, 8, 'G').box(0, 4, 16, 1, 'g');
    for (const x of [1, 5, 9, 13]) i.box(x, 6, 2, 3, 'Z');
    for (let x = 1; x < 15; x += 2) i.box(x, 11, 1, 2, 'Y');
    i.px(7, 11, 'G');
  },
  particles(i) {
    const star = (x, y, c, big) => {
      i.px(x, y, 'W').px(x - 1, y, c).px(x + 1, y, c).px(x, y - 1, c).px(x, y + 1, c);
      if (big) i.px(x - 2, y, c).px(x + 2, y, c).px(x, y - 2, c).px(x, y + 2, c);
    };
    star(5, 5, 'y', true);
    star(11, 10, 'P', true);
    star(12, 3, 'O', false);
    star(4, 12, 'L', false);
  },
  armorstatus(i) {
    i.map(2, 2, ['sWss....ssss', 'ssssssssssss', 'ssssssssssSs', '.ssssssssss.', '..ssssssss..', '..ssssSsss..', '..ssssssss..', '..ssssSsss..', '..ssssssss..', '..SSSSSSSS..']);
    i.box(7, 3, 2, 1, 'S');
  },
  coordinates(i) {
    i.box(0, 6, 16, 9, 't').box(0, 6, 16, 1, 'n');
    i.ln(1, 12, 5, 9, 'N').ln(5, 9, 10, 11, 'N').ln(10, 11, 15, 8, 'N');
    i.map(5, 0, ['.rrrr.', 'rrWrrr', 'rWwwrr', 'rrwwrr', '.rrrr.', '..rr..', '...r..'.replace('...r..', '..rr..'), '...r..']);
  },
  potioneffects(i) {
    i.box(6, 0, 4, 2, 'n').box(6, 2, 4, 3, 'L');
    i.dot(8, 10, 5, 'L').dot(8, 10, 4, 'p').box(4, 7, 9, 2, 'L').px(5, 9, 'P').px(10, 12, 'W').px(7, 13, 'P');
    i.box(5, 8, 7, 1, 'v');
  },
  blockoverlay(i) {
    i.map(0, 0, [
      '......OOOO......', '....OOVVVVOO....', '..OOVVVVVVVVOO..', 'OOVVVVVVVVVVVVOO', 'OVOOVVVVVVVVOOVO',
      'OVvvOOVVVVOOppVO', 'OVvvvvOOOOppppVO', 'OVvvvvvOOpppppVO', 'OVvvvvvOOpppppVO', 'OVvvvvvOOpppppVO',
      'OVvvvvvOOpppppVO', 'OOvvvvvOOpppppOO', '..OOvvvOOpppOO..', '....OOvOOpOO....', '......OOOO......'
    ]);
  },
  crosshair(i) {
    i.box(7, 1, 2, 5, 'w').box(7, 10, 2, 5, 'w').box(1, 7, 5, 2, 'w').box(10, 7, 5, 2, 'w').box(7, 7, 2, 2, 'o');
  },
  fireoverlay(i) {
    i.map(2, 0, [
      '.....o......', '....oo......', '....ooo..o..', '...oOoo..oo.', '..ooOOoo.oo.', '..oOOOoooOo.', '.ooOyOOooOoo', '.oOyyOOOOOOo',
      'ooOyyyOOyOOo', 'oOyyyyOyyyOo', 'oOyyWyyyyyOo', 'oOyyWWyyyyOo', '.oOyyyyyyOo.', '..ooOOOOoo..', '....oooo....'
    ]);
  },
  fullbright(i) {
    i.dot(8, 8, 4, 'Y').dot(8, 8, 3, 'y').px(7, 6, 'W');
    for (const [x, y] of [[8, 1], [8, 15], [1, 8], [15, 8], [3, 3], [13, 13], [3, 13], [13, 3]]) i.px(x, y, 'Y');
    for (const [x, y] of [[8, 2], [8, 14], [2, 8], [14, 8]]) i.px(x, y, 'Y');
  },
  hurtcam(i) {
    i.map(1, 2, ['.rrrr..rrrr.', 'rrWrrrrrrrrr', 'rWrrrkrrrrRr', 'rrrrrrkrrrRr', 'rrrrrkrrrRRr', '.rrrrrkrrRr.', '..rrrkrrRr..', '...rrrkRr...', '....rrRr....', '.....rr.....']);
    i.px(0, 3, 'w').px(14, 5, 'w').px(15, 9, 'w');
  },
  itemphysics(i) {
    i.box(0, 13, 16, 3, 'G').box(0, 13, 16, 1, 'g');
    i.map(3, 9, ['..YYYYYY', '.YyyyyYd', 'YYYYYYd.', 'dddddd..']);
    i.map(6, 0, ['.P.', '.P.', '.P.', 'PPP', '.P.'].map((r) => r)).map(10, 2, ['.P.', '.P.', 'PPP', '.P.']);
  },
  scoreboard(i) {
    i.box(2, 0, 12, 16, 'Z').box(2, 0, 12, 3, 'o').box(4, 1, 8, 1, 'y');
    for (const y of [5, 8, 11, 14]) i.box(4, y, 6, 1, 'w').px(12, y, 'r');
  },
  timechanger(i) {
    for (let y = 1; y < 16; y++) {
      for (let x = 1; x < 16; x++) {
        if ((x - 8) ** 2 + (y - 8) ** 2 > 40) continue;
        if (x < 8) i.px(x, y, 'y');
        else if ((x - 11) ** 2 + (y - 6) ** 2 > 14) i.px(x, y, 'P');
      }
    }
    i.box(8, 1, 1, 14, 'k');
    i.px(4, 5, 'W').px(0, 8, 'Y').px(1, 3, 'Y').px(1, 13, 'Y').px(14, 2, 'w').px(15, 12, 'L');
  },
  weatherchanger(i) {
    i.dot(5, 6, 3, 'w').dot(10, 5, 4, 'w').box(2, 6, 12, 4, 'w').box(2, 9, 12, 1, 's');
    for (const [x, y] of [[3, 12], [7, 13], [11, 12], [5, 15], [9, 15], [13, 15]]) i.px(x, y, 'b').px(x, y - 1, 'b');
  },
  biome(i) {
    i.box(7, 12, 2, 4, 'n');
    i.map(2, 0, ['.....G......', '....GgG.....', '...GgggG....', '....GgG.....', '..GggggGG...', '.GgggggggG..', '...GgggG....', '..GgggggGG..', '.GggggggggG.', 'GGgggggggGGG', '...GGGGGG...', '............'].map((r) => r.padEnd(12, '.')));
    i.box(0, 14, 16, 2, 'G');
  },
  direction(i) {
    i.dot(8, 8, 7, 'Y').dot(8, 8, 6, 'd').dot(8, 8, 5, 'w');
    i.map(6, 3, ['..r..', '.rrr.', 'rrrrr', '..k..', 'SSSSS', '.SSS.', '..S..'].slice(0, 7));
    i.box(8, 6, 1, 1, 'o');
  },
  waypoints(i) {
    i.box(4, 0, 3, 13, 'P').box(5, 0, 1, 13, 'L');
    i.box(3, 1, 1, 13, 'n').box(3, 1, 1, 1, 'Y');
    i.map(4, 1, ['oooooooo', 'oOOOOOoo', 'ooooooo.', 'oooooo..', 'oooooooo', 'dddddddd']);
    i.box(0, 13, 9, 3, 'G').box(0, 13, 9, 1, 'g');
  },

  // ---------------------------------------------------------------- categories
  'cat-all'(i) {
    i.box(1, 1, 6, 6, 'p').box(9, 1, 6, 6, 'o').box(1, 9, 6, 6, 'o').box(9, 9, 6, 6, 'p');
    i.px(1, 1, 'P').px(9, 1, 'O').px(1, 9, 'O').px(9, 9, 'P');
  },
  'cat-combat'(i) {
    i.map(1, 1, ['..........ss', '.........sWs', '........sWs.', '.......sWs..', '......sWs...', '..Y..sWs....', '..YYsWs.....', '...YYs......', '..nYYY......', '.nn..Y......', 'nn..........', 'n...........']);
  },
  'cat-movement'(i) {
    boot(i, 4, 6);
    i.map(0, 2, ['wwww......', '..wwww....', 'wwww......']).map(9, 0, ['..L', '.LL', 'LL.']);
  },
  'cat-player'(i) {
    i.box(5, 1, 6, 6, 'n').box(6, 3, 1, 1, 'k').box(9, 3, 1, 1, 'k').box(5, 1, 6, 2, 'N');
    i.box(3, 8, 10, 8, 'p').box(3, 8, 10, 1, 'P').box(7, 8, 2, 2, 'n');
  },
  'cat-render'(i) {
    eye(i, 2, 4, 'o');
  },
  'cat-world'(i) {
    i.map(0, 0, [
      '......GGGG......', '....GGggggGG....', '..GGggggggggGG..', 'GGggggggggggggGG', 'nGGGggggggggGGGt',
      'nnnnGGGggGGGttt t'.replace(' ', ''), 'nNnnnnGGGGtttttt', 'nnnNnnnnttttnttt', 'nnnnnnNnttttttnt', 'nNnnnnnntttntttt',
      'nnnnNnnnttttttnt', 'nnnnnnnnttnttttt', '..nnnNnntttttt..', '....nnnnttnt....', '......nntt......'
    ]);
  },
  'cat-hud'(i) {
    i.box(0, 1, 16, 14, 's').box(1, 2, 14, 12, 'V');
    i.box(2, 3, 5, 2, 'O').box(10, 3, 4, 3, 'P').box(2, 11, 4, 2, 'g').box(9, 11, 5, 2, 'r');
  },
  'cat-misc'(i) {
    i.map(1, 1, [
      '......GG......', '.....GgG......', '..DDDdDDdDDD..', '.DooOooOooOoD.', 'DooOOooOOooOoD', 'DoyyyoOoOyyyoD', 'DooyyoOoOyyooD',
      'DooOooyOOooOoD', 'DoOoOyyyOoOooD', 'DoyoyyyyyyoyoD', 'DooyyoyyoyyooD', '.DooOooOooOoD.', '..DDDDDDDDDD..'
    ]);
  },
  'cat-performance'(i) {
    i.map(3, 0, ['.....YYYY', '....YyyY.', '...YyyY..', '..YyyY...', '.YyyYYYY.', 'YyyyyyyY.', 'YYYYyyY..', '...YyY...', '..YyY....', '..YY.....', '.YY......', '.Y.......'].map((r) => r.padEnd(10, '.')));
  },

  // ---------------------------------------------------------------- menu (title screen, quick actions)
  singleplayer(i) {
    i.map(0, 0, [
      '.......dd.......', '......dDDd......', '.....dDDDDd.....', '....dDDDDDDd....', '...dDDDDDDDDd...', '..dDDDDDDDDDDd..', '.dddddddddddddd.', '..nnnnnnnnnnnn..',
      '..nyynnnnnyynn..', '..nyynNNnnyynn..', '..nnnnNNnnnnnn..', '..nnnnNNnnnnnn..', '..nnnnNNnnnnnn..', '..NNNNNNNNNNNN..', '..SSSSSSSSSSSS..', '................'
    ]);
  },
  multiplayer(i) {
    i.box(1, 3, 6, 6, 'n').box(1, 3, 6, 2, 'N').px(2, 5, 'k').px(5, 5, 'k');
    i.box(0, 10, 8, 6, 'o').box(0, 10, 8, 1, 'O');
    i.box(9, 1, 6, 6, 't').box(9, 1, 6, 2, 'D').px(10, 3, 'k').px(13, 3, 'k');
    i.box(8, 8, 8, 8, 'p').box(8, 8, 8, 1, 'P');
  },
  mods(i) {
    i.map(0, 2, [
      '.vvv......PPP...', 'vvvvv....PPPPP..', 'vvvvvv..PPPPPPP.', 'vvvvvvv.PPPPPPPP', 'vvv.vvvPPPP.PPPP', 'vv...vvvPPP..PPP',
      'v.....vvvP.....P', '..vvv.vvvP.PPP..', '.vvvvvvvvPPPPPP.', 'vvvvvvvvPPPPPPPP', 'vvvvvvvvPPPPPPPP'
    ]);
  },
  options(i) {
    i.dot(8, 8, 5, 's').dot(8, 8, 2, null);
    for (const [x, y] of [[8, 1], [8, 14], [1, 8], [14, 8], [3, 3], [13, 13], [3, 13], [13, 3]]) i.box(x - 1, y - 1, 3, 3, 's');
    i.dot(8, 8, 2, 'Z').px(8, 8, 'k');
    i.px(6, 5, 'W');
  },
  quit(i) {
    i.box(2, 0, 10, 16, 'N').box(3, 1, 8, 15, 'n').box(3, 1, 8, 1, 't');
    i.box(4, 2, 6, 5, 'N').box(4, 9, 6, 5, 'N').px(9, 8, 'Y');
    i.map(11, 5, ['.....', '..O..', '...O.', 'OOOOO', '...O.', '..O..']);
  },
  language(i) {
    i.dot(8, 8, 7, 'B').dot(8, 8, 6, 'b');
    i.map(3, 3, ['.gggg.....', 'gggggg....', '.ggggg..gg', '..ggg..ggg', '...g...ggg', '.......gg.', '......gg..', '..........']);
    i.box(9, 9, 7, 7, 'w').mini(11, 10, 'X', 'v');
  },
  cosmetics(i) {
    i.map(0, 0, [
      '........Vv......', '.......VvvV.....', '......VvvvV.....', '.....VvvvvV.....', '.....VvvvvvV....', '....VvvvvvvV....', '....VvvvvvvvV...', '...VvoooooovV...',
      '...VoOOOOOOoV...', '..VVvvvvvvvvVV..', 'VVVVVVVVVVVVVVVV', 'VvvvvvvvvvvvvvvV', '.VVVVVVVVVVVVVV.'
    ]);
  },
  hudedit(i) {
    i.box(0, 0, 16, 16, 'Z').box(1, 1, 14, 14, 'M');
    i.box(3, 3, 5, 3, 'O').box(9, 9, 4, 4, 'P');
    i.map(5, 6, ['.w.', 'www', '.w.']).ln(6, 9, 6, 12, 'w').ln(3, 12, 9, 12, 'w');
  },
  settings(i) {
    DRAW.options(i);
  },
  profiles(i) {
    i.box(3, 0, 12, 9, 'v').box(2, 3, 12, 9, 'p').box(1, 6, 12, 9, 'P').box(2, 7, 10, 2, 'w').box(2, 10, 7, 1, 'p').box(2, 12, 8, 1, 'p');
  },
  waypointsMenu(i) {
    DRAW.waypoints(i);
  },

  news(i) {
    i.map(0, 2, [
      '.NNNNNN.NNNNNN..', 'NwwwwwwNwwwwwwN.', 'NwmmmmwNwmmmmwN.', 'NwwwwwwNwwwwwwN.', 'NwmmmwwNwmmmwwN.', 'NwwwwwwNwwwwwwN.',
      'NwmmmmwNwmmmmwN.', 'NwwwwwwNwwwwwwN.', 'NwmmwwwNwmmmwwN.', 'NwwwwwwNwwwwwwN.', 'NNNNNNNnNNNNNNN.', '......nnn.......'
    ]);
    i.box(11, 0, 2, 5, 'o').px(11, 5, 'o').px(12, 5, 'D');
  },
  play(i) {
    i.map(3, 1, ['oo........', 'oOoo......', 'oOOOoo....', 'oOOOOOoo..', 'oOOOOOOOoo', 'oOOOOOOOoo', 'oOOOOOoo..', 'oOOOoo....', 'oOoo......', 'oo........'].map((r) => r.replace(/O/g, 'O')));
    i.box(3, 11, 1, 1, 'd');
    for (let y = 1; y < 11; y++) i.px(3, y, 'd');
  },
  moon(i) {
    i.dot(8, 8, 6, 'y').dot(11, 6, 5, 'k');
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (i.get(x, y) === PAL.ink) i.clear(x, y);
    i.px(4, 6, 'W').px(5, 11, 'Y').px(7, 13, 'Y').px(13, 3, 'w').px(14, 10, 'L');
  },

  // ---------------------------------------------------------------- interface
  search(i) {
    i.dot(6, 6, 4, 'm').dot(6, 6, 3, 'B').px(5, 4, 'b').px(4, 5, 'b');
    i.ln(9, 9, 13, 13, 'n').ln(10, 9, 14, 13, 'n').ln(9, 10, 13, 14, 'N');
  },
  close(i) {
    i.ln(4, 4, 11, 11, 'w').ln(5, 4, 12, 11, 'w').ln(11, 4, 4, 11, 'w').ln(12, 4, 5, 11, 'w');
  },
  lock(i) {
    i.map(4, 1, ['..ssss..', '.s....s.', '.s....s.', '.s....s.']);
    i.box(3, 5, 10, 9, 'Y').box(3, 5, 10, 1, 'y').box(3, 13, 10, 1, 'd').box(7, 8, 2, 3, 'D');
  },
  back(i) {
    i.map(2, 3, ['....w.....', '...ww.....', '..wwwwwwww', '.wwwwwwwww', '..wwwwwwww', '...ww.....', '....w.....']);
  }
};

/** Draws icon {@code name}, outlined. */
export function icon(name) {
  const draw = DRAW[name];
  if (!draw) throw new Error(`no icon ${name}`);
  const i = new Icon();
  draw(i);
  // the outline needs a free pixel: shrink-wrap inside the 16×16 cell
  const out = new Canvas(16, 16);
  out.draw(i, 0, 0);
  out.outline(PAL.ink, false);
  return out;
}

export const MODULE_ICONS = [
  'combo', 'cps', 'healthtags', 'hitcolor', 'reach', 'targethud', 'clock', 'fps', 'keystrokes', 'ping', 'sessioninfo', 'speed',
  'stopwatch', 'autogg', 'chat', 'screenshot', 'serverinfo', 'freelook', 'togglesneak', 'togglesprint', 'zoom', 'dynamicfps',
  'entityculling', 'fpsgraph', 'memory', 'particles', 'armorstatus', 'coordinates', 'potioneffects', 'blockoverlay', 'crosshair',
  'fireoverlay', 'fullbright', 'hurtcam', 'itemphysics', 'scoreboard', 'timechanger', 'weatherchanger', 'biome', 'direction', 'waypoints'
];
export const CATEGORY_ICONS = ['cat-all', 'cat-combat', 'cat-movement', 'cat-player', 'cat-render', 'cat-world', 'cat-hud', 'cat-misc', 'cat-performance'];
export const MENU_ICONS = ['singleplayer', 'multiplayer', 'mods', 'options', 'quit', 'language', 'cosmetics', 'hudedit', 'settings', 'profiles', 'waypointsMenu'];
export const UI_ICONS = ['search', 'close', 'lock', 'back', 'news', 'play', 'moon'];
export const ALL_ICONS = [...MODULE_ICONS, ...CATEGORY_ICONS, ...MENU_ICONS, ...UI_ICONS];
