import { defineCommand } from 'citty';
import { $ } from 'zx';
import { GRADLEW, REPO_ROOT, SERVICES_DIR } from '../lib/paths.ts';

const build = defineCommand({
    meta: { name: 'build', description: 'Run ./services/gradlew build' },
    async run() {
        await $({ cwd: SERVICES_DIR })`${GRADLEW} build`;
    },
});

const test = defineCommand({
    meta: { name: 'test', description: 'Run ./services/gradlew test' },
    async run() {
        await $({ cwd: SERVICES_DIR })`${GRADLEW} test`;
    },
});

const format = defineCommand({
    meta: {
        name: 'format',
        description: 'Format Java/KTS/XML, proto, and root files.',
    },
    async run() {
        await $({ cwd: SERVICES_DIR })`${GRADLEW} spotlessApply`;
        await $({
            cwd: SERVICES_DIR,
        })`${GRADLEW} -p services/proto bufFormatApply`;
        await $({ cwd: REPO_ROOT })`pnpm format`;
    },
});

const lint = defineCommand({
    meta: { name: 'lint', description: 'Lint markdown' },
    async run() {
        await $({ cwd: REPO_ROOT })`pnpm lint`;
    },
});

const openapi = defineCommand({
    meta: {
        name: 'openapi',
        description: 'Regenerate per-service OpenAPI specs and lint them',
    },
    async run() {
        await $({ cwd: REPO_ROOT })`pnpm run openapi`;
    },
});

export const quality = {
    build,
    test,
    format,
    lint,
    openapi,
};
