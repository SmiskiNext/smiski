import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const moduleDirectory = dirname(fileURLToPath(import.meta.url));

/** Absolute path to the monorepo root, independent of the caller's cwd. */
export const repositoryRoot = resolve(moduleDirectory, '../../..');

/** Absolute path to the load-test stack directory. */
export const testStackDirectory = resolve(repositoryRoot, 'services/test');

/**
 * Absolute path to the generated key material directory.
 *
 * Gitignored: it holds the private key that signs Forge Invocation Tokens the
 * test stack accepts as authentic.
 */
export const testKeyDirectory = resolve(testStackDirectory, 'keys');

/** Absolute path to the test stack's compose overlay. */
export const testComposeFile = resolve(testStackDirectory, 'compose.yaml');

/** Absolute path to the k6 scripts mounted into the load generator. */
export const k6ScriptDirectory = resolve(testStackDirectory, 'k6');

/**
 * Absolute path to the directory measurement artifacts are written to.
 *
 * Gitignored: every file under it is a measurement result rather than source.
 */
export const testResultsDirectory = resolve(testStackDirectory, 'results');
