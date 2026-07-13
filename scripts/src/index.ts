#!/usr/bin/env node
import { defineCommand, runMain } from "citty";
import { $ } from "zx";
import { doctor } from "./commands/doctor.js";
import { infra, infraDown, infraUp } from "./commands/infra.js";
import { setup } from "./commands/setup.js";
import { svc, svcAll } from "./commands/svc.js";
import { quality } from "./commands/quality.js";
import { applyIpConfig } from "./lib/ip.js";

$.verbose = true;

const ip = defineCommand({
    meta: {
        name: "ip",
        description: "Update all config files with the given LAN IP",
    },
    args: {
        ip: {
            type: "positional",
            description: "LAN IP address to apply (e.g. 192.168.1.100)",
            required: true,
        },
    },
    async run({ args }) {
        applyIpConfig(args.ip);
    },
});

const dev = defineCommand({
    meta: {
        name: "dev",
        description:
            "Start infra (wait for healthy), then run all 4 backend services in parallel",
    },
    args: {
        ip: {
            type: "string",
            description:
                "LAN IP to expose services on (updates .env files for FE, BE, and Caddy)",
        },
    },
    async run({ args }) {
        if (args.ip) {
            applyIpConfig(args.ip);
        }
        await infraUp();
        await svcAll();
    },
});

const clean = defineCommand({
    meta: {
        name: "clean",
        description: "Alias for 'infra down' (stop infra, keep volumes)",
    },
    run: infraDown,
});

const main = defineCommand({
    meta: {
        name: "smiski",
        version: "0.0.0",
        description: "Zero Meeting System developer CLI",
    },
    subCommands: {
        setup,
        doctor,
        ip,
        dev,
        clean,
        infra,
        svc,
        build: quality.build,
        test: quality.test,
        format: quality.format,
        lint: quality.lint,
        openapi: quality.openapi,
    },
});

await runMain(main);
