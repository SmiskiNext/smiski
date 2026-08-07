/**
 * Identity of the running bundle, used to scope everything this app caches
 * outside its own memory.
 *
 * Two consumers depend on it. The persisted query cache
 * (`hooks/queryPersistence.ts`) tags stored data with this value and discards
 * anything carrying a different one, so a redeployed bundle never hydrates a
 * cache whose shape predates it. The Jira permission-key resolution
 * (`api/meetingPermission.ts`) is keyed by it, because those keys are derived
 * from the permissions this bundle's `manifest.yml` declares and can only
 * change when the app is redeployed.
 *
 * `vite.config.ts` injects the value, honouring `SMISKI_BUILD_ID` from the
 * environment and falling back to the build timestamp. The fallback here covers
 * the test runner, where no `define` has been applied — a fixed value is
 * correct there, since a test process has no deploy to invalidate.
 */

const DEVELOPMENT_BUILD_VERSION = 'dev';

function readBuildVersion(): string {
    const injected = import.meta.env.SMISKI_BUILD_ID?.trim();
    return injected ? injected : DEVELOPMENT_BUILD_VERSION;
}

export const BUILD_VERSION = readBuildVersion();
