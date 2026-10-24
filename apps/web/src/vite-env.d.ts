/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Synthetic ASR preview; disabled in ordinary demos/builds until FARO-040. Never enables the backend by itself. */
  readonly VITE_FARO_SYNTHETIC_ASR?: string
}
