import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

/**
 * Variables shared verbatim with `app/manifest.yml`.
 *
 * The Forge CLI interpolates these same names into the manifest `${...}`
 * placeholders from its process environment, so a single value per concept
 * reaches both the manifest and this bundle. `SMISKI_API_BASE_URL` and
 * `LIVEKIT_URL` MUST stay byte-identical to the manifest's
 * `permissions.external.fetch` entries — the SSE client and the LiveKit
 * client call those origins directly, and Forge blocks any origin that is
 * not declared.
 */
const DEPLOYMENT_VARIABLES = [
    'SMISKI_API_BASE_URL',
    'SMISKI_API_VERSION',
    'LIVEKIT_URL',
] as const;

const PROJECT_ROOT = fileURLToPath(new URL('.', import.meta.url));

/**
 * Exposes the deployment variables to `import.meta.env`.
 *
 * Vite forwards only `VITE_`-prefixed variables and rejects an empty
 * `envPrefix`, so unprefixed names must be injected through `define`.
 * Process environment values win over `.env` files, matching how the Forge
 * CLI resolves manifest placeholders.
 */
function defineDeploymentVariables(mode: string): Record<string, string> {
    const env = loadEnv(mode, PROJECT_ROOT, '');
    return Object.fromEntries(
        DEPLOYMENT_VARIABLES.map((key) => [
            `import.meta.env.${key}`,
            JSON.stringify(env[key] ?? ''),
        ]),
    );
}

/**
 * Identifies the built bundle for the persisted query cache.
 *
 * `src/hooks/queryPersistence.ts` tags persisted `localStorage` data with this
 * value and discards anything carrying a different one, so a redeployed bundle
 * never restores a cache whose shape predates it. It also scopes the cached
 * Jira permission-key resolution, which is only invalidated by a redeploy.
 *
 * `SMISKI_BUILD_ID` from the environment wins so CI can pin a commit SHA and
 * keep a build reproducible; the build timestamp is the fallback, which changes
 * on every local build and is therefore never staler than the code.
 *
 * Deliberately kept out of {@link DEPLOYMENT_VARIABLES}: those must stay
 * byte-identical to the manifest's declared egress, and this value reaches only
 * the bundle.
 */
function defineBuildId(mode: string): Record<string, string> {
    const env = loadEnv(mode, PROJECT_ROOT, '');
    const buildId = env.SMISKI_BUILD_ID?.trim() || new Date().toISOString();
    return { 'import.meta.env.SMISKI_BUILD_ID': JSON.stringify(buildId) };
}

/**
 * Port the dev server binds, kept in sync with `app/manifest.yml`'s
 * `resources[key: main].tunnel.port` so `forge tunnel` proxies the Custom UI
 * to this server. `strictPort` fails fast instead of drifting to the next free
 * port, which would leave the tunnel proxying an address nothing listens on.
 */
const DEV_SERVER_PORT = 5173;

/**
 * Vite config for the Smiski Custom UI bundle.
 *
 * - `base: './'` — Forge serves the bundle from a relative path, so assets must
 *   be referenced relatively (equivalent to CRA's `homepage: "."`).
 * - `outDir: 'dist'` — the manifest `main` resource points at static/smiski-ui/dist.
 * - `@` alias — resolves to `src/`, mirrored in tsconfig.json `paths`.
 * - Tailwind owns the complete visual layer. Forge/Jira theme information is
 *   translated to app CSS variables in ThemeProvider.
 * - `define` — mirrors the manifest deployment variables into the bundle, plus
 *   the build identifier the persisted query cache is tagged with.
 * - `server` — pinned for `forge tunnel`; the app renders only inside a real
 *   Forge module, so this server is reached through the tunnel, never directly.
 */
export default defineConfig(({ mode }) => ({
    plugins: [react(), tailwindcss()],
    base: './',
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    define: {
        ...defineDeploymentVariables(mode),
        ...defineBuildId(mode),
    },
    server: {
        port: DEV_SERVER_PORT,
        strictPort: true,
    },
    build: {
        outDir: 'dist',
        emptyOutDir: true,
    },
}));
