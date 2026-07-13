import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { DOCKER_ENV } from './paths.ts';

const DOCKER_ENV_REPLACEMENTS: Record<string, (ip: string) => string> = {
    ZMS_HOST_IP: (ip) => ip,
    LIVEKIT_URL: (ip) => `http://${ip}:7880`,
    LIVEKIT_WS_URL: (ip) => `ws://${ip}:30000/livekit`,
    GATEWAY_URL: (ip) => `http://${ip}:30000`,
    INVITATION_JOIN_BASE_URL: (ip) => `http://${ip}:3000/join`,
    LIVEKIT_RECORDING_PUBLIC_ENDPOINT: (ip) => `http://${ip}:9000`,
};

function updateEnvFile(
    path: string,
    replacements: Record<string, (ip: string) => string>,
    ip: string,
): void {
    if (!existsSync(path)) {
        console.warn(`[ip] Skipping ${path} (file not found)`);
        return;
    }
    let content = readFileSync(path, 'utf8');
    for (const [key, valueFn] of Object.entries(replacements)) {
        const regex = new RegExp(`^(${key})=.*$`, 'm');
        const newLine = `${key}=${valueFn(ip)}`;
        if (regex.test(content)) {
            content = content.replace(regex, newLine);
        } else {
            content += `\n${newLine}`;
        }
    }
    writeFileSync(path, content, 'utf8');
    console.log(`[ip] Updated ${path}`);
}

export function applyIpConfig(ip: string): void {
    console.log(`[ip] Applying IP ${ip} to dev config files...`);
    updateEnvFile(DOCKER_ENV, DOCKER_ENV_REPLACEMENTS, ip);
}
