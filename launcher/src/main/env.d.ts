declare module '*?raw' {
  const content: string;
  export default content;
}

/** The owner's public configuration, embedded at build time from `.env` (see core/config.ts). */
declare const __MEDIRIAN_CONFIG__: Partial<Record<
  'MEDIRIAN_MSA_CLIENT_ID' | 'MEDIRIAN_DISCORD_APP_ID' | 'MEDIRIAN_SERVICES_URL' | 'MEDIRIAN_MANIFEST_URL' | 'MEDIRIAN_CONTACT',
  string
>>;
