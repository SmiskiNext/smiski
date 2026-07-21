import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/**
 * Vite config for the Smiski Custom UI bundle.
 *
 * - `base: './'` — Forge serves the bundle from a relative path, so assets must
 *   be referenced relatively (equivalent to CRA's `homepage: "."`).
 * - `outDir: 'dist'` — the manifest `main` resource points at static/smiski-ui/dist.
 * - `@` alias — resolves to `src/`, mirrored in tsconfig.json `paths`.
 * - Tailwind owns the complete visual layer. Forge/Jira theme information is
 *   translated to app CSS variables in ThemeProvider.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: './',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});
