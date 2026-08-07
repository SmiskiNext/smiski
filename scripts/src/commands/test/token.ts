import { defineCommand } from 'citty';
import {
    buildFitClaims,
    DEFAULT_APP_ARI,
    DEFAULT_ENVIRONMENT_ARI,
    FIT_AUDIENCE,
    loadSigningKey,
    signFit,
} from '../../lib/fit.ts';
import { HARNESS_ACCOUNT_ID, HARNESS_TENANT_ID } from '../../lib/fixtures.ts';
import { testKeyDirectory } from '../../lib/paths.ts';

const DEFAULT_LIFETIME_SECONDS = 3600;

export const tokenCommand = defineCommand({
    meta: {
        name: 'token',
        description:
            'Sign a Forge Invocation Token the test stack accepts as authentic',
    },
    args: {
        'cloud-id': {
            type: 'string',
            description:
                'Tenant identifier; must equal the seeded meetings.tenant_id',
            default: HARNESS_TENANT_ID,
        },
        'account-id': {
            type: 'string',
            description: 'Account the token authenticates as',
            default: HARNESS_ACCOUNT_ID,
        },
        'app-ari': {
            type: 'string',
            description: 'Forge application ARI written to the app.id claim',
            default: DEFAULT_APP_ARI,
        },
        'environment-ari': {
            type: 'string',
            description:
                'Forge environment ARI written to the app.environment.id claim',
            default: DEFAULT_ENVIRONMENT_ARI,
        },
        audience: {
            type: 'string',
            description: 'Audience claim; must match the Envoy provider',
            default: FIT_AUDIENCE,
        },
        'expires-in': {
            type: 'string',
            description: 'Token lifetime in seconds',
            default: String(DEFAULT_LIFETIME_SECONDS),
        },
        'key-dir': {
            type: 'string',
            description: 'Directory holding the generated key material',
            default: testKeyDirectory,
        },
        'omit-app-id': {
            type: 'boolean',
            description:
                'Sign without app.id, to verify the parser rejects it (negative test)',
            default: false,
        },
        'omit-environment-id': {
            type: 'boolean',
            description:
                'Sign without app.environment.id, to verify the parser rejects it (negative test)',
            default: false,
        },
        claims: {
            type: 'boolean',
            description: 'Print the signed claim set instead of only the token',
            default: false,
        },
    },
    async run({ args }) {
        const expiresInSeconds = Number.parseInt(args['expires-in'], 10);
        if (!Number.isFinite(expiresInSeconds) || expiresInSeconds <= 0) {
            console.error(
                `--expires-in must be a positive number of seconds, got ${args['expires-in']}`,
            );
            process.exitCode = 1;
            return;
        }

        const key = await loadSigningKey(args['key-dir']).catch(
            (error: unknown) => error as Error,
        );

        if (key instanceof Error) {
            console.error(key.message);
            console.error(
                'Run `pnpm --dir scripts smiski test keygen` first: the private key '
                    + 'signs tokens and the JWKS supplies the key id Envoy trusts.',
            );
            process.exitCode = 1;
            return;
        }

        const claims = buildFitClaims({
            cloudId: args['cloud-id'],
            accountId: args['account-id'],
            appAri: args['app-ari'],
            environmentAri: args['environment-ari'],
            audience: args.audience,
            expiresInSeconds,
        });

        applyClaimOmissions(claims, {
            omitAppId: args['omit-app-id'],
            omitEnvironmentId: args['omit-environment-id'],
        });

        const token = signFit(claims, key);

        if (args.claims) {
            console.error(JSON.stringify(claims, null, 4));
        }

        console.log(token);
    },
});

/**
 * Removes a required claim so the gateway parser's rejection can be exercised.
 *
 * Both omissions produce a token whose SIGNATURE is valid — Envoy accepts it and
 * the request reaches the external authorization call, where `resolveARISegment`
 * fails. That distinction is the point: it proves claim-set validation is
 * enforced independently of signature verification.
 */
function applyClaimOmissions(
    claims: Record<string, unknown>,
    omissions: { omitAppId: boolean; omitEnvironmentId: boolean },
): void {
    const app = claims.app as Record<string, unknown>;

    if (omissions.omitAppId) {
        delete app.id;
    }

    if (omissions.omitEnvironmentId) {
        delete app.environment;
    }
}
