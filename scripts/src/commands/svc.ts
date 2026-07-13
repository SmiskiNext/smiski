import { defineCommand } from 'citty';
import { $ } from 'zx';
import { springDevEnv } from '../lib/env.ts';
import { runParallelWithKillAll } from '../lib/parallel.ts';
import { type BackendService, GRADLEW, SERVICES_DIR } from '../lib/paths.ts';

async function bootRun(service: BackendService): Promise<void> {
    const env = springDevEnv();
    await $({
        cwd: SERVICES_DIR,
        env: { ...process.env, ...env },
    })`${GRADLEW} -p ${service} bootRun`;
}

async function preWarmGradle(): Promise<void> {
    console.log(
        'Pre-warming build-logic to avoid parallel Kotlin daemon races...',
    );
    await $({
        cwd: SERVICES_DIR,
    })`${GRADLEW} -p user-management help -q`;
}

export async function svcAll(): Promise<void> {
    await preWarmGradle();
    console.log('Starting 4 services in parallel...');
    const env = { ...process.env, ...springDevEnv() };
    const services: BackendService[] = [
        'user-management',
        'meeting-management',
        'chat-management',
        'notification',
    ];
    const code = await runParallelWithKillAll(
        services.map((service) => ({
            label: service,
            command: GRADLEW,
            args: ['-p', `${service}`, 'bootRun'],
            cwd: SERVICES_DIR,
            env,
        })),
    );
    if (code !== 0) {
        process.exitCode = code;
    }
}

const all = defineCommand({
    meta: {
        name: 'all',
        description: 'Run all 4 backend services in parallel',
    },
    run: svcAll,
});

const user = defineCommand({
    meta: { name: 'user', description: 'Run user-management' },
    async run() {
        await bootRun('user-management');
    },
});

const meeting = defineCommand({
    meta: { name: 'meeting', description: 'Run meeting-management' },
    async run() {
        await bootRun('meeting-management');
    },
});

const chat = defineCommand({
    meta: { name: 'chat', description: 'Run chat-management' },
    async run() {
        await bootRun('chat-management');
    },
});

const notification = defineCommand({
    meta: { name: 'notification', description: 'Run notification' },
    async run() {
        await bootRun('notification');
    },
});

export const svc = defineCommand({
    meta: {
        name: 'svc',
        description: 'Run Spring Boot backend services natively',
    },
    subCommands: { all, user, meeting, chat, notification },
});
