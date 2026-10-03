import type { Argument } from '../minecraft/mojang';
import { rulesAllow } from '../minecraft/rules';

/** Expands Mojang argument lists (strings or rule-guarded values) for the active features. */
export function expandArguments(args: Argument[] | undefined, features: Record<string, boolean>): string[] {
  const result: string[] = [];
  for (const arg of args ?? []) {
    if (typeof arg === 'string') {
      result.push(arg);
    } else if (rulesAllow(arg.rules, features)) {
      result.push(...(Array.isArray(arg.value) ? arg.value : [arg.value]));
    }
  }
  return result;
}

/** Replaces ${placeholders}; unknown placeholders are left untouched. */
export function substitute(args: string[], vars: Record<string, string>): string[] {
  return args.map((arg) => arg.replace(/\$\{([a-zA-Z0-9_]+)\}/g, (match, key: string) => (key in vars ? vars[key] : match)));
}

/** Splits user supplied JVM arguments, honouring single and double quotes. */
export function splitArgs(input: string): string[] {
  const result: string[] = [];
  const pattern = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(input)) !== null) {
    result.push(match[1] ?? match[2] ?? match[3]);
  }
  return result;
}

/** Mojang's recommended G1 settings for Minecraft (also used by the official launcher). */
export const DEFAULT_GC_ARGS = [
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+UseG1GC',
  '-XX:G1NewSizePercent=20',
  '-XX:G1ReservePercent=20',
  '-XX:MaxGCPauseMillis=50',
  '-XX:G1HeapRegionSize=32M'
];

/** Removes the user's duplicates of flags we set (e.g. -Xmx) so the profile value wins. */
export function mergeJvmArgs(base: string[], user: string[]): string[] {
  const userKeys = new Set(user.map(flagKey));
  return [...base.filter((arg) => !userKeys.has(flagKey(arg))), ...user];
}

function flagKey(arg: string): string {
  if (arg.startsWith('-Xmx') || arg.startsWith('-Xms') || arg.startsWith('-Xss')) {
    return arg.slice(0, 4);
  }
  if (arg.startsWith('-XX:+') || arg.startsWith('-XX:-')) {
    return '-XX:' + arg.slice(5).split('=')[0];
  }
  if (arg.startsWith('-XX:')) {
    return '-XX:' + arg.slice(4).split('=')[0];
  }
  if (arg.startsWith('-D')) {
    return arg.split('=')[0];
  }
  return arg;
}
