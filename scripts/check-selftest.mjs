#!/usr/bin/env node
// Checks the output of `./gradlew runClient -Pselftest`: the walkthrough finished, the mixin audit
// passed and no check reported "Self-test FAILED".
//
// Usage: node scripts/check-selftest.mjs <log file>
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/** Returns the problems found in a self-test log (empty when it passed). */
export function selfTestProblems(log) {
  const problems = [];
  for (const line of log.split(/\r?\n/)) {
    if (line.includes('Self-test FAILED') || line.includes('Mixin audit failed')) {
      problems.push(line.trim());
    }
  }
  if (!log.includes('Mixin audit passed')) {
    problems.push('the mixin audit did not pass (or did not run)');
  }
  if (!log.includes('Self-test finished')) {
    problems.push('the self-test did not finish');
  }
  return problems;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const file = process.argv[2];
  if (!file) {
    console.error('Usage: node scripts/check-selftest.mjs <log file>');
    process.exit(2);
  }
  const problems = selfTestProblems(readFileSync(file, 'utf8'));
  for (const problem of problems) {
    console.log(`FAIL ${problem}`);
  }
  if (problems.length > 0) {
    process.exit(1);
  }
  console.log('Self-test passed');
}
