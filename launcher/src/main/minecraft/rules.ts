import { arch, platform, release } from 'node:os';

/** Mojang library/argument rule. */
export interface Rule {
  action: 'allow' | 'disallow';
  os?: { name?: string; version?: string; arch?: string };
  features?: Record<string, boolean>;
}

export type OsName = 'windows' | 'osx' | 'linux';

export function osName(): OsName {
  switch (platform()) {
    case 'win32':
      return 'windows';
    case 'darwin':
      return 'osx';
    default:
      return 'linux';
  }
}

/** "32" or "64", used by `${arch}` in legacy native classifiers. */
export function archBits(): '32' | '64' {
  return arch() === 'ia32' || arch() === 'arm' ? '32' : '64';
}

/**
 * Evaluates Mojang rules: without rules everything is allowed; otherwise the last matching
 * rule decides.
 */
export function rulesAllow(rules: Rule[] | undefined, features: Record<string, boolean> = {}): boolean {
  if (!rules || rules.length === 0) {
    return true;
  }
  let allowed = false;
  for (const rule of rules) {
    if (matches(rule, features)) {
      allowed = rule.action === 'allow';
    }
  }
  return allowed;
}

function matches(rule: Rule, features: Record<string, boolean>): boolean {
  if (rule.os) {
    if (rule.os.name && rule.os.name !== osName()) {
      return false;
    }
    if (rule.os.arch && !(rule.os.arch === 'x86' ? arch() === 'ia32' : rule.os.arch === arch())) {
      return false;
    }
    if (rule.os.version) {
      try {
        if (!new RegExp(rule.os.version).test(release())) {
          return false;
        }
      } catch {
        return false;
      }
    }
  }
  if (rule.features) {
    for (const [feature, value] of Object.entries(rule.features)) {
      if ((features[feature] ?? false) !== value) {
        return false;
      }
    }
  }
  return true;
}
