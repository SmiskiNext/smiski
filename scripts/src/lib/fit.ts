import { createPrivateKey, createSign } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { testKeyDirectory } from './paths.ts';

const SIGNING_ALGORITHM = 'RS256';
const NODE_SIGN_ALGORITHM = 'RSA-SHA256';

/**
 * Issuer the Envoy `forge_fit` provider requires.
 *
 * Envoy matches this literally against the `iss` claim, so it is not
 * configurable: a different value is rejected with a 401 before any filter
 * downstream of `jwt_authn` runs.
 */
export const FIT_ISSUER = 'forge/invocation-token';

/**
 * Audience the Envoy `forge_fit` provider requires.
 *
 * Mirrors the single entry in `audiences` in both `services/docker/envoy/envoy.yaml`
 * and the test overlay. Changing it in one place only produces a 401.
 */
export const FIT_AUDIENCE =
    'ari:cloud:ecosystem::app/5e00d851-2813-4c0b-b447-c4fd5ba7b150';

/** Default Forge application ARI, shaped as `resolveARISegment` expects. */
export const DEFAULT_APP_ARI =
    'ari:cloud:ecosystem::app/5e00d851-2813-4c0b-b447-c4fd5ba7b150';

/** Default Forge environment ARI, shaped as `resolveARISegment` expects. */
export const DEFAULT_ENVIRONMENT_ARI =
    'ari:cloud:ecosystem::environment/5e00d851-2813-4c0b-b447-c4fd5ba7b150'
    + '/2f9a1d84-7e6b-4c53-9f21-8d0e6b3a5c17';

/**
 * Every claim the gateway's Forge Invocation Token parser reads.
 *
 * Derived from `services/gateway/internal/fit/parser.go` rather than from
 * Atlassian's documentation, because the parser is what actually rejects a
 * token. Three of these are load bearing in ways that are not obvious:
 *
 * - `cloudId` becomes the tenant identifier, so it must equal the seeded
 *   `meetings.tenant_id`. A mismatch passes authentication and then yields 404
 *   from Hibernate's `@TenantId` filter, which reads as a missing fixture.
 * - `appAri` and `environmentAri` both pass through `resolveARISegment`, which
 *   errors on an absent claim and on an empty trailing segment. A token omitting
 *   either is rejected during authorization, not during signature verification.
 * - `apiBaseUrl` is the fallback source of the cloud identifier and is what the
 *   inherited `extract_claims.lua` reads, so its trailing segment must agree
 *   with `cloudId`.
 */
export interface FitClaimInput {
    cloudId: string;
    accountId: string;
    appAri: string;
    environmentAri: string;
    audience: string;
    expiresInSeconds: number;
}

/** A signed token together with the claims that were signed into it. */
export interface SignedFit {
    token: string;
    claims: Record<string, unknown>;
}

/** Key material read from the directory `keygen` wrote. */
export interface SigningKey {
    keyId: string;
    privateKeyPem: string;
}

function base64UrlEncode(value: Buffer | string): string {
    const buffer =
        typeof value === 'string' ? Buffer.from(value, 'utf8') : value;
    return buffer.toString('base64url');
}

function encodeSegment(value: unknown): string {
    return base64UrlEncode(JSON.stringify(value));
}

/**
 * Builds the claim payload in the exact shape the gateway parser expects.
 *
 * `apiBaseUrl` is derived from `cloudId` rather than accepted separately so the
 * two can never disagree — the parser prefers `context.cloudId` while the Lua
 * filter reads only `app.apiBaseUrl`, and a token where they differ would send
 * one tenant to the gateway and another to the backend services.
 */
export function buildFitClaims(
    input: FitClaimInput,
    issuedAt: number = Math.floor(Date.now() / 1000),
): Record<string, unknown> {
    return {
        iss: FIT_ISSUER,
        aud: input.audience,
        iat: issuedAt,
        exp: issuedAt + input.expiresInSeconds,
        principal: input.accountId,
        context: {
            cloudId: input.cloudId,
        },
        app: {
            id: input.appAri,
            installationId: `ari:cloud:ecosystem::installation/${input.cloudId}`,
            apiBaseUrl: `https://api.atlassian.com/ex/jira/${input.cloudId}`,
            appVersion: '1.0.0',
            environment: {
                id: input.environmentAri,
            },
        },
    };
}

/**
 * Signs a claim payload as an RS256 compact JWS.
 *
 * The `kid` header is what pairs the token with the single key in the generated
 * JWKS. Envoy selects the verification key by `kid` when the header carries
 * one, so a stale identifier fails verification even while the key material
 * itself is valid.
 */
export function signFit(
    claims: Record<string, unknown>,
    key: SigningKey,
): string {
    const header = {
        alg: SIGNING_ALGORITHM,
        typ: 'JWT',
        kid: key.keyId,
    };

    const signingInput = `${encodeSegment(header)}.${encodeSegment(claims)}`;
    const signer = createSign(NODE_SIGN_ALGORITHM);
    signer.update(signingInput);

    const signature = signer.sign(createPrivateKey(key.privateKeyPem));

    return `${signingInput}.${base64UrlEncode(signature)}`;
}

/**
 * Reads the private key and resolves the key identifier from the generated JWKS.
 *
 * The identifier is read back from the JWKS rather than taken as an argument so
 * a token can never be signed with a `kid` Envoy does not know about.
 */
export async function loadSigningKey(
    keyDirectory: string = testKeyDirectory,
): Promise<SigningKey> {
    const privateKeyPath = resolve(keyDirectory, 'private.pem');
    const jwksPath = resolve(keyDirectory, 'jwks.json');

    const [privateKeyPem, jwksContent] = await Promise.all([
        readFile(privateKeyPath, 'utf8'),
        readFile(jwksPath, 'utf8'),
    ]);

    const jwks = JSON.parse(jwksContent) as {
        keys?: Array<{ kid?: unknown }>;
    };
    const keyId = jwks.keys?.[0]?.kid;

    if (typeof keyId !== 'string' || keyId === '') {
        throw new Error(
            `${jwksPath} carries no usable key id; re-run \`smiski test keygen --force\``,
        );
    }

    return { keyId, privateKeyPem };
}
