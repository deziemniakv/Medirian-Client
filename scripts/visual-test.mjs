#!/usr/bin/env node
// Visual regression test: compares the menu screenshots of `./gradlew runClient -Pselftest`
// with the baselines in client/visual-baselines/<target>/.
//
//   node scripts/visual-test.mjs                    compare every target that has baselines
//   node scripts/visual-test.mjs --target mc-1.8.9  only this target (repeatable)
//   node scripts/visual-test.mjs --update           replace the baselines with the current screenshots
//
// Only screens without a world behind them are compared (the self-test runs them with animations,
// toasts and seasonal themes off), so GPU and driver differences do not matter. Live numbers such
// as the FPS in the HUD editor preview change a few pixels: a screenshot fails when more than
// --max-ratio of its pixels (default 0.5%) differ by more than --threshold (default 32 per channel).
// Diff images (differences in red) are written to client/targets/<target>/run/visual-diff/.
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compareImages, decodePng, encodePng } from './lib/png.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const BASELINES = join(ROOT, 'client', 'visual-baselines');
const TARGETS = join(ROOT, 'client', 'targets');
/** Self-test screenshots without a world behind them. */
const SCREENS = ['1-modmenu', '2-hudeditor', '3-settings'];

function parseArgs(argv) {
  const options = { targets: [], update: false, threshold: 32, maxRatio: 0.005 };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--target') options.targets.push(argv[++i]);
    else if (arg === '--update') options.update = true;
    else if (arg === '--threshold') options.threshold = Number(argv[++i]);
    else if (arg === '--max-ratio') options.maxRatio = Number(argv[++i]);
    else throw new Error(`unknown argument ${arg}`);
  }
  return options;
}

const screenshotOf = (target, name) => join(TARGETS, target, 'run', 'screenshots', `selftest-${name}.png`);

function update(targets) {
  for (const target of targets) {
    const dir = join(BASELINES, target);
    mkdirSync(dir, { recursive: true });
    for (const name of SCREENS) {
      const source = screenshotOf(target, name);
      if (!existsSync(source)) throw new Error(`missing ${source} — run the self-test first`);
      copyFileSync(source, join(dir, `${name}.png`));
      console.log(`baseline updated: ${target}/${name}.png`);
    }
  }
}

function compare(targets, { threshold, maxRatio }) {
  let failures = 0;
  for (const target of targets) {
    const dir = join(BASELINES, target);
    const diffDir = join(TARGETS, target, 'run', 'visual-diff');
    for (const file of readdirSync(dir).filter((f) => f.endsWith('.png')).sort()) {
      const name = file.slice(0, -4);
      const actualPath = screenshotOf(target, name);
      if (!existsSync(actualPath)) {
        console.log(`FAIL ${target}/${name}: no screenshot (did the self-test run?)`);
        failures++;
        continue;
      }
      const result = compareImages(decodePng(readFileSync(join(dir, file))), decodePng(readFileSync(actualPath)), threshold);
      const percent = (result.ratio * 100).toFixed(3);
      if (result.sizeMismatch) {
        console.log(`FAIL ${target}/${name}: different size`);
        failures++;
      } else if (result.ratio > maxRatio) {
        mkdirSync(diffDir, { recursive: true });
        writeFileSync(join(diffDir, `${name}.png`), encodePng(result.diff));
        console.log(`FAIL ${target}/${name}: ${result.differing} pixels differ (${percent}%), diff in ${join(diffDir, `${name}.png`)}`);
        failures++;
      } else {
        console.log(`ok   ${target}/${name}: ${result.differing} pixels differ (${percent}%)`);
      }
    }
  }
  return failures;
}

const options = parseArgs(process.argv.slice(2));
const available = existsSync(BASELINES) ? readdirSync(BASELINES) : [];
const targets = options.targets.length > 0 ? options.targets : options.update ? readdirSync(TARGETS) : available;
if (options.update) {
  update(targets);
} else {
  const missing = targets.filter((t) => !available.includes(t));
  if (missing.length > 0) throw new Error(`no baselines for ${missing.join(', ')} — create them with --update`);
  const failures = compare(targets, options);
  if (failures > 0) {
    console.log(`${failures} screenshot(s) differ from the baselines. If the change is intended, run with --update.`);
    process.exit(1);
  }
}
