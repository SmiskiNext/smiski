import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const moduleDirectory = dirname(fileURLToPath(import.meta.url));

/** Absolute path to the monorepo root, independent of the caller's cwd. */
export const repositoryRoot = resolve(moduleDirectory, '../../..');

/** Absolute path to the local development stack directory. */
export const dockerStackDirectory = resolve(repositoryRoot, 'services/docker');

/** Absolute path to the development stack's compose definition. */
export const dockerComposeFile = resolve(dockerStackDirectory, 'compose.yaml');

/**
 * Absolute path to the development stack's environment file.
 *
 * Gitignored, and read by compose from the compose file's own directory rather
 * than from the caller's cwd, so the stack sees the same values whichever
 * directory a command is run from.
 */
export const dockerEnvFile = resolve(dockerStackDirectory, '.env');

/** Absolute path to the tracked template the environment file is copied from. */
export const dockerEnvExample = resolve(dockerStackDirectory, '.env.example');

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
