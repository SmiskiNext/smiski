import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

const packageJson = JSON.parse(readFileSync('package.json', 'utf8'));
const tarball = `smiskinext-smiski-ts-${packageJson.version}.tgz`;
const smokeTest = `
import assert from 'node:assert/strict';
import {
    createClient,
    list,
    zMeetScheduleMeetingRequest,
} from '@smiskinext/smiski-ts';

assert.equal(typeof zMeetScheduleMeetingRequest.safeParse, 'function');

let fetchCalls = 0;
const fetch = async () => {
    fetchCalls += 1;
    return new Response(JSON.stringify({ data: 'malformed' }), {
        headers: { 'Content-Type': 'application/json' },
        status: 200,
    });
};
const client = createClient({ baseUrl: 'https://sdk.invalid', fetch });
const isValidationError = (error) => error?.name === 'ZodError';

await assert.rejects(
    list({ client, path: { version: 'invalid' } }),
    isValidationError,
);
assert.equal(fetchCalls, 0);

await assert.rejects(
    list({ client, path: { version: 1 } }),
    isValidationError,
);
assert.equal(fetchCalls, 1);
`;

const directory = mkdtempSync(resolve(tmpdir(), 'smiski-sdk-'));

try {
    writeFileSync(
        resolve(directory, 'package.json'),
        '{"private":true,"type":"module"}\n',
    );
    writeFileSync(resolve(directory, 'smoke-test.mjs'), smokeTest);
    execFileSync(
        'npm',
        [
            'install',
            '--ignore-scripts',
            '--no-audit',
            '--no-fund',
            resolve('.pack', tarball),
        ],
        { cwd: directory, stdio: 'inherit' },
    );
    execFileSync(process.execPath, ['smoke-test.mjs'], {
        cwd: directory,
        stdio: 'inherit',
    });
} finally {
    rmSync(directory, { force: true, recursive: true });
}
