/**
 * Runtime configuration read from the deployment variables that
 * `vite.config.ts` injects into the bundle. The same variable names are
 * interpolated into `app/manifest.yml` by the Forge CLI, so the origins used
 * here always match the manifest's declared egress.
 */
const DEFAULT_API_VERSION = 1;

function readOptional(value: string | undefined): string | undefined {
    const trimmed = value?.trim();
    return trimmed ? trimmed : undefined;
}

function readApiVersion(): number {
    const value = Number(
        readOptional(import.meta.env.SMISKI_API_VERSION) ?? DEFAULT_API_VERSION,
    );
    return Number.isInteger(value) && value > 0 ? value : DEFAULT_API_VERSION;
}

function readApiBaseUrl(): string | undefined {
    return readOptional(import.meta.env.SMISKI_API_BASE_URL)?.replace(
        /\/$/,
        '',
    );
}

export const apiConfig = {
    apiBaseUrl: readApiBaseUrl(),
    apiVersion: readApiVersion(),
    liveKitUrl: readOptional(import.meta.env.LIVEKIT_URL),
};
