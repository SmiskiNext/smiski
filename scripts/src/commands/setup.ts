import { copyFileSync, existsSync } from "node:fs";
import { defineCommand } from "citty";
import { $ } from "zx";
import {
    DOCKER_ENV,
    DOCKER_ENV_EXAMPLE,
    REPO_ROOT,
    WEB_DIR,
} from "../lib/paths.js";

async function ensureMiseAvailable(): Promise<void> {
    const result = await $({ nothrow: true, quiet: true })`mise --version`;
    if (result.exitCode !== 0) {
        throw new Error(
            "`mise` is required but was not found on PATH.\n" +
                "Install it from https://mise.jdx.dev/getting-started.html and re-run this command.",
        );
    }
}

async function installMiseTools(): Promise<void> {
    console.log("→ Trusting and installing tools declared in .mise.toml");
    await $({ cwd: REPO_ROOT })`mise trust --quiet ${REPO_ROOT}`;
    await $({ cwd: REPO_ROOT })`mise install`;
}

async function installPnpmDeps(): Promise<void> {
    console.log("→ Installing pnpm dependencies (root + workspace)");
    await $({ cwd: REPO_ROOT })`pnpm install --recursive`;
    await $({ cwd: WEB_DIR })`pnpm install`;
}

async function installLefthookHooks(): Promise<void> {
    console.log("→ Registering lefthook git hooks");
    const result = await $({ cwd: REPO_ROOT, nothrow: true })`lefthook install`;
    if (result.exitCode !== 0) {
        throw new Error(
            "`lefthook install` failed. Ensure mise has installed lefthook and git is initialized.",
        );
    }
}

function ensureDockerEnv(): void {
    if (existsSync(DOCKER_ENV)) {
        console.log(`→ ${DOCKER_ENV} already exists — skipping`);
        return;
    }
    copyFileSync(DOCKER_ENV_EXAMPLE, DOCKER_ENV);
    console.log(
        `→ Created ${DOCKER_ENV} — review and rotate secrets before sharing`,
    );
}

export const setup = defineCommand({
    meta: {
        name: "setup",
        description:
            "Bootstrap the dev environment: mise tools, pnpm deps, git hooks, and docker .env. Idempotent.",
    },
    args: {
        "env-only": {
            type: "boolean",
            description:
                "Only copy services/docker/.env from .env.example; skip tool/dep install.",
            default: false,
        },
    },
    async run({ args }) {
        if (args["env-only"]) {
            ensureDockerEnv();
            return;
        }
        await ensureMiseAvailable();
        await installMiseTools();
        await installPnpmDeps();
        await installLefthookHooks();
        ensureDockerEnv();
        console.log("✓ Setup complete. Run `pnpm smiski doctor` to verify.");
    },
});
