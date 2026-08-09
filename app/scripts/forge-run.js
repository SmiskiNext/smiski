#!/usr/bin/env node
/**
 * Cross-platform Forge CLI wrapper.
 * Automatically loads app/.env into process.env so FORGE_EMAIL and FORGE_API_TOKEN
 * bypass the Windows Keychain issue for all forge CLI subcommands.
 */

const { spawnSync } = require('node:child_process');
const path = require('node:path');
const { loadDotEnv } = require('./load-dotenv');

loadDotEnv();

const args = process.argv.slice(2);
const forgeCli = path.resolve(
    __dirname,
    '..',
    'node_modules',
    '@forge',
    'cli',
    'out',
    'bin',
    'cli.js',
);
const result = spawnSync(process.execPath, [forgeCli, ...args], {
    stdio: 'inherit',
    env: process.env,
    cwd: path.resolve(__dirname, '..'),
});

if (result.error) {
    console.error(`error: could not start Forge CLI: ${result.error.message}`);
    process.exit(1);
}

process.exit(result.status ?? 1);
