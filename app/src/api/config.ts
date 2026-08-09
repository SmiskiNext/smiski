/**
 * Runtime configuration for the Forge lifecycle functions.
 *
 * The Custom UI reads the same concept from `import.meta.env`
 * (`static/smiski-ui/src/api/config.ts`), which a function cannot: a function
 * runs in the Forge Node runtime, where deployment values arrive as
 * environment variables (`forge variables set`). Only the API version is read
 * here — the backend origin is the `meet-backend` remote's `baseUrl`, resolved
 * by the Forge platform, so the functions never name it.
 */
const DEFAULT_API_VERSION = 1;

/**
 * The `{version}` path segment of the versioned API routes, per the
 * `api-convention` spec. Falls back to the only deployed version so a
 * lifecycle event is never dropped over an unset variable.
 */
function readApiVersion(): number {
    const value = Number(process.env.SMISKI_API_VERSION ?? DEFAULT_API_VERSION);
    return Number.isInteger(value) && value > 0 ? value : DEFAULT_API_VERSION;
}

export const apiConfig = {
    apiVersion: readApiVersion(),
};
