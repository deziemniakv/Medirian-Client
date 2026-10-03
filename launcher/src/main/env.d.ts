declare module '*?raw' {
  const content: string;
  export default content;
}

interface ImportMetaEnv {
  /** Azure application id for Microsoft login, injected at build time (MAIN_VITE_MSA_CLIENT_ID). */
  readonly MAIN_VITE_MSA_CLIENT_ID?: string;
  /** Discord application id for Rich Presence, injected at build time (MAIN_VITE_DISCORD_APP_ID). */
  readonly MAIN_VITE_DISCORD_APP_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
