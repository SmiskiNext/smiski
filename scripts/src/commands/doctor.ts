import { defineCommand } from "citty";
import {
    checkBinary,
    checkDocker,
    checkDockerEnv,
    checkMise,
    formatCheck,
    type CheckResult,
} from "../lib/checks.js";

async function gatherChecks(): Promise<CheckResult[]> {
    return [
        await checkMise(),
        await checkBinary("java", ["--version"], {
            hint: "Run `pnpm smiski setup` (mise will install Java 25).",
        }),
        await checkBinary("node", ["--version"], {
            hint: "Run `pnpm smiski setup` (mise will install Node).",
        }),
        await checkBinary("pnpm", ["--version"], {
            hint: "Run `pnpm smiski setup` (mise will install pnpm).",
        }),
        await checkBinary("gitleaks", ["version"], {
            hint: "Run `pnpm smiski setup` (mise will install gitleaks).",
        }),
        await checkBinary("lefthook", ["version"], {
            hint: "Run `pnpm smiski setup` (mise will install lefthook + git hooks).",
        }),
        await checkBinary("buf", ["--version"], {
            hint: "Run `pnpm smiski setup` (mise will install buf).",
        }),
        await checkDocker(),
        await checkBinary("mongosh", ["--version"], {
            hint: "Run `pnpm smiski setup` (mise will install mongosh via npm) — required by Flyway MongoDB native connector for chat-management migrations.",
            warnIfMissing: true,
        }),
        checkDockerEnv(),
    ];
}

export const doctor = defineCommand({
    meta: {
        name: "doctor",
        description:
            "Inspect the developer environment and report tool status without changing anything.",
    },
    async run() {
        const results = await gatherChecks();
        const okCount = results.filter((r) => r.status === "ok").length;
        const warnCount = results.filter((r) => r.status === "warn").length;
        const missingCount = results.filter((r) => r.status === "missing").length;

        console.log("Zero Meeting System — environment check");
        console.log("=========================================");
        for (const result of results) {
            console.log(formatCheck(result));
        }
        console.log("-----------------------------------------");
        console.log(
            `Summary: ${okCount} ok, ${warnCount} warn, ${missingCount} missing`,
        );
        if (missingCount > 0) {
            console.log("Run `pnpm smiski setup` to install missing tools.");
        }
    },
});
