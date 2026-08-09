#!/usr/bin/env node
/**
 * Cross-platform deploy runner for Forge.
 * Loads app/.env into process.env so FORGE_EMAIL and FORGE_API_TOKEN
 * bypass the Windows Keychain issue automatically during forge deploy.
 */

const { spawnSync } = require('node:child_process');
const path = require('node:path');
const { loadDotEnv } = require('./load-dotenv');

loadDotEnv();

const appDirectory = path.resolve(__dirname, '..');
const uiDirectory = path.join(appDirectory, 'static', 'smiski-ui');
const viteCli = path.join(
    uiDirectory,
    'node_modules',
    'vite',
    'bin',
    'vite.js',
);
const forgeCli = path.join(
    appDirectory,
    'node_modules',
    '@forge',
    'cli',
    'out',
    'bin',
    'cli.js',
);

function runStep(command, args, options = {}) {
    console.log(`> ${command} ${args.join(' ')}`);
    const result = spawnSync(command, args, {
        stdio: 'inherit',
        env: process.env,
        ...options,
    });
    if (result.error) {
        console.error(
            `error: could not start ${command}: ${result.error.message}`,
        );
        process.exit(1);
    }
    if (result.status !== 0) {
        process.exit(result.status ?? 1);
    }
}

// 1. Build UI
runStep(process.execPath, [viteCli, 'build'], { cwd: uiDirectory });

// 2. Render manifest
runStep(process.execPath, [path.join(__dirname, 'render-manifest.js')], {
    cwd: appDirectory,
});

// 3. Deploy with the workspace-pinned Forge CLI
runStep(process.execPath, [forgeCli, 'deploy', ...process.argv.slice(2)], {
    cwd: appDirectory,
});
