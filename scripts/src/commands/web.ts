import { defineCommand } from "citty";
import { $ } from "zx";
import { WEB_DIR } from "../lib/paths.js";

export const web = defineCommand({
    meta: { name: "web", description: "Start Next.js dev server" },
    async run() {
        await $`pnpm --dir ${WEB_DIR} dev`;
    },
});
