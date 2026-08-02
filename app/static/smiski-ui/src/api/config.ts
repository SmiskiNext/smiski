export type ApiDataSource = 'mock' | 'backend';

function readDataSource(): ApiDataSource {
    return import.meta.env.VITE_SMISKI_DATA_SOURCE === 'backend'
        ? 'backend'
        : 'mock';
}

function readApiVersion(): number {
    const value = Number(import.meta.env.VITE_SMISKI_API_VERSION ?? 1);
    return Number.isInteger(value) && value > 0 ? value : 1;
}

function readApiBaseUrl(): string | undefined {
    const value = import.meta.env.VITE_SMISKI_API_BASE_URL?.trim();
    return value ? value.replace(/\/$/, '') : undefined;
}

export const apiConfig = {
    dataSource: readDataSource(),
    apiBaseUrl: readApiBaseUrl(),
    apiVersion: readApiVersion(),
    liveKitUrl: import.meta.env.VITE_SMISKI_LIVEKIT_URL,
};
