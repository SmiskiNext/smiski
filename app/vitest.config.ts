import { defineConfig } from 'vitest/config';

/**
 * Test runner for the Forge lifecycle functions in `src/`.
 *
 * Scoped explicitly to `src/`: `static/smiski-ui` is a separate workspace
 * package with its own `vitest` invocation, and vitest's default `include`
 * would otherwise sweep its suite into this run as well.
 *
 * The functions never touch the DOM — they run in the Forge Node runtime — so
 * the default `node` environment applies and no per-file pragma is needed.
 */
export default defineConfig({
    test: {
        include: ['src/**/*.test.ts'],
    },
});
