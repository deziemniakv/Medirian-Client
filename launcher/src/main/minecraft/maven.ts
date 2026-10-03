/** Maven coordinate helpers (group:artifact:version[:classifier][@extension]). */

export interface MavenCoordinate {
  group: string;
  artifact: string;
  version: string;
  classifier?: string;
  extension: string;
}

export function parseCoordinate(name: string): MavenCoordinate {
  const [coordinate, extension = 'jar'] = name.split('@');
  const parts = coordinate.split(':');
  if (parts.length < 3) {
    throw new Error(`Invalid maven coordinate: ${name}`);
  }
  return { group: parts[0], artifact: parts[1], version: parts[2], classifier: parts[3], extension };
}

/** Relative repository path, e.g. net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar */
export function mavenPath(name: string): string {
  const c = parseCoordinate(name);
  const file = `${c.artifact}-${c.version}${c.classifier ? `-${c.classifier}` : ''}.${c.extension}`;
  return `${c.group.replace(/\./g, '/')}/${c.artifact}/${c.version}/${file}`;
}

/** Key used to de-duplicate libraries: group:artifact[:classifier] (version excluded). */
export function libraryKey(name: string): string {
  const c = parseCoordinate(name);
  return `${c.group}:${c.artifact}${c.classifier ? `:${c.classifier}` : ''}`;
}
