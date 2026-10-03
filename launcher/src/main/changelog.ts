import type { ChangelogEntry } from '../common/types';
// The changelog is bundled at build time from the repository root.
import changelogSource from '../../../CHANGELOG.md?raw';

/**
 * Parses CHANGELOG.md:
 *
 *   ## 0.1.0 — 2026-10-03 — Title
 *   - item
 */
export function parseChangelog(markdown: string): ChangelogEntry[] {
  const entries: ChangelogEntry[] = [];
  let current: ChangelogEntry | null = null;
  for (const raw of markdown.split(/\r?\n/)) {
    const line = raw.trim();
    const heading = line.match(/^##\s+([^\s—-]+)\s*[—-]\s*(\d{4}-\d{2}-\d{2})(?:\s*[—-]\s*(.+))?$/);
    if (heading) {
      current = { version: heading[1], date: heading[2], title: heading[3]?.trim() ?? '', items: [] };
      entries.push(current);
    } else if (current && /^[-*]\s+/.test(line)) {
      current.items.push(line.replace(/^[-*]\s+/, ''));
    }
  }
  return entries;
}

export const CHANGELOG: ChangelogEntry[] = parseChangelog(changelogSource);
