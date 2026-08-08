/// <reference types="vite/client" />

/**
 * Deployment variables injected by `vite.config.ts` and shared verbatim with
 * `app/manifest.yml`. They carry no `VITE_` prefix because the Forge CLI
 * interpolates the same names into the manifest, keeping one value per
 * concept across the manifest and the bundle.
 *
 * `SMISKI_BUILD_ID` is the exception: it never reaches the manifest and is
 * generated at build time when unset. `utils/buildVersion.ts` reads it to tag
 * the persisted query cache, so a redeployed bundle discards a cache written
 * by its predecessor.
 */
interface ImportMetaEnv {
    readonly SMISKI_API_BASE_URL?: string;
    readonly SMISKI_API_VERSION?: string;
    readonly LIVEKIT_URL?: string;
    readonly SMISKI_BUILD_ID?: string;
}
