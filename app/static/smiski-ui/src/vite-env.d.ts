/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SMISKI_DATA_SOURCE?: 'mock' | 'backend';
  readonly VITE_SMISKI_API_TRANSPORT?: 'resolver' | 'direct';
  readonly VITE_SMISKI_API_BASE_URL?: string;
  readonly VITE_SMISKI_API_VERSION?: string;
  readonly VITE_SMISKI_LIVEKIT_URL?: string;
}
