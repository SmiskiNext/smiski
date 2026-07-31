/**
 * Loads `app/.env` into the environment, then runs the given command.
 *
 * Exists because the Forge CLI needs two things from the shell that Windows
 * can't supply here:
 *   1. `FORGE_EMAIL`/`FORGE_API_TOKEN` — `forge login` fails to write the
 *      local keychain on this machine, so token auth via env vars is the
 *      supported fallback.
 *   2. `SMISKI_API_BASE_URL`/`LIVEKIT_URL` — the CLI interpolates `${VAR}`
 *      in `manifest.yml` from `process.env` at deploy time (falling back to
 *      each variable's `default:`), so these must be set before `forge deploy`
 *      runs, not after.
 *
 * Real shell variables always win over `.env`, so CI (which injects secrets
 * directly) is unaffected. A missing `.env` is not an error — the manifest
 * defaults then apply.
 *
 * Usage: node scripts/with-env.mjs forge deploy
 */
import { spawn } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const APP_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const ENV_PATH = join(APP_ROOT, '.env');

/**
 * Parses `KEY=VALUE` lines. Splits on the FIRST `=` only, since API tokens
 * routinely contain `=` padding. Strips one layer of matching quotes and
 * trailing `\r` (CRLF checkouts on Windows).
 */
function parseEnvFile(contents) {
    const values = {};
    for (const rawLine of contents.split('\n')) {
        const line = rawLine.replace(/\r$/, '').trim();
        if (!line || line.startsWith('#')) continue;

        const separator = line.indexOf('=');
        if (separator === -1) continue;

        const key = line.slice(0, separator).trim();
        if (!key) continue;

        let value = line.slice(separator + 1).trim();
        if (
            (value.startsWith("'") && value.endsWith("'"))
            || (value.startsWith('"') && value.endsWith('"'))
        ) {
            value = value.slice(1, -1);
        }
        values[key] = value;
    }
    return values;
}

function loadEnvFile() {
    let contents;
    try {
        contents = readFileSync(ENV_PATH, 'utf8');
    } catch (error) {
        if (error.code === 'ENOENT') return [];
        throw error;
    }

    const loaded = [];
    for (const [key, value] of Object.entries(parseEnvFile(contents))) {
        // Never clobber a real shell variable — that is the CI override path.
        if (process.env[key] !== undefined) continue;
        process.env[key] = value;
        loaded.push(key);
    }
    return loaded;
}

const [command, ...args] = process.argv.slice(2);
if (!command) {
    console.error('Usage: node scripts/with-env.mjs <command> [args...]');
    process.exit(2);
}

const loaded = loadEnvFile();
if (loaded.length) {
    // Names only — never echo values, these are credentials.
    console.log(`Loaded from .env: ${loaded.join(', ')}`);
}

/**
 * `shell: true` hands the command line to the shell, which re-splits it on
 * whitespace — so anything containing a space (a path, `--since "15 m"`) must
 * be quoted here or it arrives as two arguments.
 */
function quoteForShell(arg) {
    return /[\s"'`$&|<>^()]/.test(arg) ? `"${arg.replace(/"/g, '\\"')}"` : arg;
}

// `shell: true` so the npm-installed `forge` shim (`forge.cmd` on Windows)
// resolves off the PATH that `pnpm run` already extends with node_modules/.bin.
const child = spawn(quoteForShell(command), args.map(quoteForShell), {
    stdio: 'inherit',
    shell: true,
    cwd: APP_ROOT,
});

child.on('error', (error) => {
    console.error(`Failed to start ${command}: ${error.message}`);
    process.exit(1);
});
child.on('exit', (code, signal) => {
    process.exit(signal ? 1 : (code ?? 0));
});
