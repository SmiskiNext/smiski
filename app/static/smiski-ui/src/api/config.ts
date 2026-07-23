export type ApiDataSource = 'mock' | 'backend';
export type ApiTransport = 'resolver' | 'direct';

function readDataSource(): ApiDataSource {
  return import.meta.env.VITE_SMISKI_DATA_SOURCE === 'backend' ? 'backend' : 'mock';
}

function readTransport(): ApiTransport {
  if (import.meta.env.VITE_SMISKI_API_TRANSPORT) {
    return import.meta.env.VITE_SMISKI_API_TRANSPORT === 'direct' ? 'direct' : 'resolver';
  }
  return import.meta.env.DEV && import.meta.env.VITE_SMISKI_API_BASE_URL ? 'direct' : 'resolver';
}

function trimTrailingSlash(value: string | undefined): string {
  return value?.replace(/\/+$/, '') ?? '';
}

function readApiVersion(): number {
  const value = Number(import.meta.env.VITE_SMISKI_API_VERSION ?? 1);
  return Number.isInteger(value) && value > 0 ? value : 1;
}

export const apiConfig = {
  dataSource: readDataSource(),
  transport: readTransport(),
  baseUrl: trimTrailingSlash(import.meta.env.VITE_SMISKI_API_BASE_URL),
  apiVersion: readApiVersion(),
  liveKitUrl: import.meta.env.VITE_SMISKI_LIVEKIT_URL,
};

export function shouldUseBackendApi(): boolean {
  return apiConfig.dataSource === 'backend';
}
