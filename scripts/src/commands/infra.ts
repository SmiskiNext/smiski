import { defineCommand } from "citty";
import { $, cd } from "zx";
import { DOCKER_DIR } from "../lib/paths.js";

export async function infraUp(): Promise<void> {
    cd(DOCKER_DIR);
    await $`docker compose up -d --wait`;
}

export async function infraDown(): Promise<void> {
    cd(DOCKER_DIR);
    await $`docker compose down`;
}

export async function infraReset(): Promise<void> {
    cd(DOCKER_DIR);
    await $`docker compose down -v`;
}

export async function infraLogs(): Promise<void> {
    cd(DOCKER_DIR);
    await $`docker compose logs -f`;
}

export async function infraPs(): Promise<void> {
    cd(DOCKER_DIR);
    await $`docker compose ps`;
}

const up = defineCommand({
    meta: { name: "up", description: "Start infra detached and wait until healthy" },
    run: infraUp,
});

const down = defineCommand({
    meta: { name: "down", description: "Stop infra (keep volumes)" },
    run: infraDown,
});

const reset = defineCommand({
    meta: {
        name: "reset",
        description: "Stop infra and remove volumes (destroys local data)",
    },
    run: infraReset,
});

const logs = defineCommand({
    meta: { name: "logs", description: "Tail infra logs" },
    run: infraLogs,
});

const ps = defineCommand({
    meta: { name: "ps", description: "List infra containers" },
    run: infraPs,
});

export const infra = defineCommand({
    meta: {
        name: "infra",
        description: "Docker Compose infrastructure controls",
    },
    subCommands: { up, down, reset, logs, ps },
});
