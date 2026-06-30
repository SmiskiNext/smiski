import { existsSync } from "node:fs";
import { $ } from "zx";
import { DOCKER_ENV } from "./paths.js";

export type CheckStatus = "ok" | "warn" | "missing";

export interface CheckResult {
    name: string;
    status: CheckStatus;
    detail: string;
    hint?: string;
}

async function tryVersion(command: string, args: string[]): Promise<string | null> {
    try {
        const result = await $({ nothrow: true, quiet: true })`${command} ${args}`;
        if (result.exitCode !== 0) {
            return null;
        }
        const output = `${result.stdout}${result.stderr}`.trim();
        const firstLine = output.split("\n")[0]?.trim() ?? "";
        return firstLine.length > 0 ? firstLine : "(no version output)";
    } catch {
        return null;
    }
}

export async function checkBinary(
    name: string,
    versionArgs: string[],
    options: { hint: string; warnIfMissing?: boolean } = { hint: "" },
): Promise<CheckResult> {
    const version = await tryVersion(name, versionArgs);
    if (version === null) {
        return {
            name,
            status: options.warnIfMissing ? "warn" : "missing",
            detail: "not found on PATH",
            hint: options.hint,
        };
    }
    return { name, status: "ok", detail: version };
}

export async function checkMise(): Promise<CheckResult> {
    return checkBinary("mise", ["--version"], {
        hint: "Install: https://mise.jdx.dev/getting-started.html",
    });
}

export async function checkDocker(): Promise<CheckResult> {
    const version = await tryVersion("docker", ["--version"]);
    if (version === null) {
        return {
            name: "docker",
            status: "warn",
            detail: "not found on PATH",
            hint: "Install Docker Engine or Docker Desktop: https://docs.docker.com/get-docker/",
        };
    }
    const ping = await $({ nothrow: true, quiet: true })`docker info`;
    if (ping.exitCode !== 0) {
        return {
            name: "docker",
            status: "warn",
            detail: `${version} (daemon not reachable)`,
            hint: "Start the Docker daemon before running `pnpm smiski infra up`.",
        };
    }
    return { name: "docker", status: "ok", detail: version };
}

export function checkDockerEnv(): CheckResult {
    if (existsSync(DOCKER_ENV)) {
        return {
            name: "services/docker/.env",
            status: "ok",
            detail: "present",
        };
    }
    return {
        name: "services/docker/.env",
        status: "warn",
        detail: "missing",
        hint: "Run `pnpm smiski setup --env-only` to create from .env.example.",
    };
}

const STATUS_GLYPH: Record<CheckStatus, string> = {
    ok: "✓",
    warn: "⚠",
    missing: "✗",
};

export function formatCheck(result: CheckResult): string {
    const glyph = STATUS_GLYPH[result.status];
    const head = `${glyph} ${result.name.padEnd(22)} ${result.detail}`;
    return result.hint ? `${head}\n    → ${result.hint}` : head;
}
