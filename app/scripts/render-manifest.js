#!/usr/bin/env node
/**
 * Substitutes deployment origins into app/manifest.yml in place.
 * Cross-platform Node.js script for Windows, macOS, and Linux.
 * Replaces remote baseUrl and external fetch addresses in manifest.yml from app/.env or process.env.
 */

const fs = require('node:fs');
const path = require('node:path');
const { loadDotEnv } = require('./load-dotenv');

const manifestFile = process.argv[2] || 'manifest.yml';
const manifestPath = path.resolve(process.cwd(), manifestFile);

if (!fs.existsSync(manifestPath)) {
    console.error(
        `error: ${manifestFile} not found; run this from the app/ directory.`,
    );
    process.exit(1);
}

const envVars = { ...process.env };
loadDotEnv(envVars);

const rawBaseUrl = envVars.SMISKI_API_BASE_URL;
if (!rawBaseUrl?.trim()) {
    console.error(
        'error: SMISKI_API_BASE_URL is not set in environment or app/.env.',
    );
    process.exit(1);
}

const smiskiApiBaseUrl = rawBaseUrl.trim();

// Forge accepts only https:// origins with no trailing slash
const urlRegex = /^https:\/\/[A-Za-z0-9.-]+(:[0-9]+)?$/;
if (!urlRegex.test(smiskiApiBaseUrl)) {
    console.error(
        `error: SMISKI_API_BASE_URL must be an https:// origin with no path or trailing slash; got '${smiskiApiBaseUrl}'.`,
    );
    process.exit(1);
}

function replaceMeetBackendBaseUrl(content, value) {
    const lines = content.split(/\r?\n/);
    const remoteIndex = lines.findIndex(
        (line) => line.trim() === '- key: meet-backend',
    );
    if (remoteIndex === -1) {
        throw new Error("manifest does not declare remote 'meet-backend'");
    }

    for (let index = remoteIndex + 1; index < lines.length; index += 1) {
        if (lines[index].trim().startsWith('- key:')) break;
        if (!lines[index].trim().startsWith('baseUrl:')) continue;

        const indentation = lines[index].match(/^\s*/)?.[0] ?? '';
        lines[index] = `${indentation}baseUrl: '${value}'`;
        return lines.join('\n');
    }

    throw new Error("remote 'meet-backend' does not declare baseUrl");
}

function replaceLiveKitClientAddress(content, value) {
    const lines = content.split(/\r?\n/);
    const clientIndex = lines.findIndex((line) => line.trim() === 'client:');
    if (clientIndex === -1) {
        throw new Error('manifest does not declare external.fetch.client');
    }

    for (let index = clientIndex + 1; index < lines.length; index += 1) {
        const trimmed = lines[index].trim();
        if (trimmed && !trimmed.startsWith('-')) break;
        if (!trimmed.startsWith('- address:')) continue;

        const indentation = lines[index].match(/^\s*/)?.[0] ?? '';
        lines[index] = `${indentation}- address: '${value}'`;
        return lines.join('\n');
    }

    throw new Error('manifest client egress does not declare an address');
}

let content = fs.readFileSync(manifestPath, 'utf-8');

try {
    content = replaceMeetBackendBaseUrl(content, smiskiApiBaseUrl);
} catch (error) {
    console.error(
        `error: ${error instanceof Error ? error.message : String(error)}`,
    );
    process.exit(1);
}

const rawLiveKitUrl = envVars.LIVEKIT_URL?.trim();
if (rawLiveKitUrl) {
    const liveKitUrlRegex = /^(?:https|wss):\/\/[A-Za-z0-9.-]+(?::[0-9]+)?$/;
    if (!liveKitUrlRegex.test(rawLiveKitUrl)) {
        console.error(
            `error: LIVEKIT_URL must be an https:// or wss:// origin with no path or trailing slash; got '${rawLiveKitUrl}'.`,
        );
        process.exit(1);
    }
    const wssUrl = rawLiveKitUrl.replace(/^https:/, 'wss:');
    try {
        content = replaceLiveKitClientAddress(content, wssUrl);
    } catch (error) {
        console.error(
            `error: ${error instanceof Error ? error.message : String(error)}`,
        );
        process.exit(1);
    }
}

fs.writeFileSync(manifestPath, content, 'utf-8');

console.log(
    `Rendered ${manifestFile} with SMISKI_API_BASE_URL=${smiskiApiBaseUrl}`,
);
