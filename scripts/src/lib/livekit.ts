import { readFileSync } from 'node:fs';
import { parse } from 'dotenv';
import { dockerEnvFile, testEnvFile } from './paths.ts';

/**
 * Defaults compose falls back to via `${LIVEKIT_API_KEY:-...}` when neither
 * `.env` sets the key. Kept identical to the literals in
 * `services/test/compose.yaml` so a stack started without a `.env` override and
 * a load generator started without one agree on the same credential.
 */
const DEFAULT_API_KEY = 'smiski-livekit-key';
const DEFAULT_API_SECRET = 'change-me-livekit-secret-must-be-at-least-32-chars';

/** LiveKit credential pair a self-signing load generator must present. */
export interface LiveKitCredentials {
    apiKey: string;
    apiSecret: string;
}

function readEnvFile(path: string): Record<string, string> {
    try {
        return parse(readFileSync(path, 'utf8'));
    } catch {
        return {};
    }
}

/**
 * Resolves the LiveKit API key and secret the running server actually accepts.
 *
 * `lk load-test` signs its own tokens from whatever credential it is handed, so
 * a value that disagrees with the server's configured key surfaces as every
 * participant failing with `invalid API key` and an empty room — never as a
 * configuration error. The order below mirrors how compose resolves the same
 * variables, so the load generator presents exactly what the stack was started
 * with:
 *
 *   1. an explicit value already in `process.env`
 *   2. `services/test/.env`, which compose loads last and lets win on a shared key
 *   3. `services/docker/.env`, the base the overlay composes
 *   4. the compose `${VAR:-default}` fallback, for a stack started without either
 */
export function resolveLiveKitCredentials(): LiveKitCredentials {
    const testEnv = readEnvFile(testEnvFile);
    const dockerEnv = readEnvFile(dockerEnvFile);

    const apiKey =
        process.env.LIVEKIT_API_KEY
        ?? testEnv.LIVEKIT_API_KEY
        ?? dockerEnv.LIVEKIT_API_KEY
        ?? DEFAULT_API_KEY;

    const apiSecret =
        process.env.LIVEKIT_API_SECRET
        ?? testEnv.LIVEKIT_API_SECRET
        ?? dockerEnv.LIVEKIT_API_SECRET
        ?? DEFAULT_API_SECRET;

    return { apiKey, apiSecret };
}
