declare module '*?raw' {
  const content: string;
  export default content;
}

interface ImportMetaEnv {
  /** Azure application id for Microsoft login, injected at build time (MAIN_VITE_MSA_CLIENT_ID). */
  readonly MAIN_VITE_MSA_CLIENT_ID?: string;
  /** Discord application id for Rich Presence, injected at build time (MAIN_VITE_DISCORD_APP_ID). */
  readonly MAIN_VITE_DISCORD_APP_ID?: string;
  /** Default release manifest URL of the stable channel, injected at build time (MAIN_VITE_MANIFEST_URL). */
  readonly MAIN_VITE_MANIFEST_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
