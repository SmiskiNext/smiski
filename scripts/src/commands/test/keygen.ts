import { generateKeyPairSync } from 'node:crypto';
import { existsSync } from 'node:fs';
import { chmod, mkdir, writeFile } from 'node:fs/promises';
import { relative, resolve } from 'node:path';
import { defineCommand } from 'citty';
import { repositoryRoot, testKeyDirectory } from '../../lib/paths.ts';

const RSA_MODULUS_LENGTH = 2048;
const SIGNING_ALGORITHM = 'RS256';
const PRIVATE_KEY_FILE_MODE = 0o600;

type JsonWebKey = Record<string, unknown>;

interface GeneratedKeyMaterial {
    privateKeyPem: string;
    jwks: { keys: JsonWebKey[] };
}

/**
 * Generates an RSA keypair and the single-key JWKS that Envoy verifies Forge
 * Invocation Tokens against.
 *
 * The public key is exported directly to JWK form by `node:crypto`, which emits
 * only `kty`, `n` and `e`. Envoy additionally matches on the key identifier and
 * algorithm, so both are added here along with the signature use.
 */
function generateKeyMaterial(keyId: string): GeneratedKeyMaterial {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', {
        modulusLength: RSA_MODULUS_LENGTH,
    });

    const publicJwk = publicKey.export({ format: 'jwk' }) as JsonWebKey;

    return {
        privateKeyPem: privateKey
            .export({ type: 'pkcs8', format: 'pem' })
            .toString(),
        jwks: {
            keys: [
                {
                    ...publicJwk,
                    kid: keyId,
                    alg: SIGNING_ALGORITHM,
                    use: 'sig',
                },
            ],
        },
    };
}

export const keygenCommand = defineCommand({
    meta: {
        name: 'keygen',
        description:
            'Generate the RSA keypair and JWKS the test stack verifies tokens against',
    },
    args: {
        'key-id': {
            type: 'string',
            description:
                'Key identifier written into the JWKS and token header',
            default: 'smiski-test-key',
        },
        'out-dir': {
            type: 'string',
            description: 'Directory to write the key material into',
            default: testKeyDirectory,
        },
        force: {
            type: 'boolean',
            description: 'Overwrite existing key material',
            default: false,
        },
    },
    async run({ args }) {
        const outputDirectory = resolve(args['out-dir']);
        const privateKeyPath = resolve(outputDirectory, 'private.pem');
        const jwksPath = resolve(outputDirectory, 'jwks.json');

        const existing = [privateKeyPath, jwksPath].filter((path) =>
            existsSync(path),
        );

        if (existing.length > 0 && !args.force) {
            const names = existing
                .map((path) => relative(repositoryRoot, path))
                .join(', ');

            console.error(
                `Refusing to overwrite existing key material: ${names}`,
            );
            console.error(
                'Envoy trusts the current JWKS and already-issued tokens are '
                    + 'signed by the current private key; regenerating invalidates '
                    + 'both. Re-run with --force to replace them.',
            );
            process.exitCode = 1;
            return;
        }

        const { privateKeyPem, jwks } = generateKeyMaterial(args['key-id']);

        await mkdir(outputDirectory, { recursive: true });
        await writeFile(privateKeyPath, privateKeyPem, 'utf8');
        await chmod(privateKeyPath, PRIVATE_KEY_FILE_MODE);
        await writeFile(jwksPath, `${JSON.stringify(jwks, null, 4)}\n`, 'utf8');

        console.log(`key id      ${args['key-id']}`);
        console.log(`private key ${relative(repositoryRoot, privateKeyPath)}`);
        console.log(`jwks        ${relative(repositoryRoot, jwksPath)}`);
        console.log(
            '\nThe private key is gitignored and must never be committed.',
        );
        console.log(
            'Envoy reads the JWKS at startup and at --mode validate time, so '
                + 'this must exist before the test stack is validated or started.',
        );
    },
});
