import { existsSync, readFileSync } from "node:fs";
import { parse } from "dotenv";
import { DOCKER_ENV } from "./paths.js";

const SECRET_ALLOWLIST = [
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "JWT_SECRET",
    "CURSOR_SECRET",
    "ZMS_INVITE_TOKEN_SECRET",
    "REDIS_PASSWORD",
    "ZMS_RESEND_API_KEY",
    "ZMS_RESEND_FROM_EMAIL",
    "ZMS_RESEND_FROM_NAME",
    "ZMS_FIREBASE_PROJECT_ID",
    "ZMS_GOOGLE_APPLICATION_CREDENTIALS",
    "CHAT_JWT_SECRET",
    "LIVEKIT_API_KEY",
    "LIVEKIT_API_SECRET",
    "LIVEKIT_URL",
    "LIVEKIT_WS_URL",
    "LIVEKIT_WEBHOOK_URL",
    "RUSTFS_ACCESS_KEY",
    "RUSTFS_SECRET_KEY",
    "LIVEKIT_RECORDING_ACCESS_KEY",
    "LIVEKIT_RECORDING_SECRET_KEY",
    "LIVEKIT_RECORDING_ENDPOINT",
    "LIVEKIT_RECORDING_PUBLIC_ENDPOINT",
    "LIVEKIT_RECORDING_EGRESS_ENDPOINT",
    "RECAPTCHA_ENABLED",
    "RECAPTCHA_SITE_KEY",
    "RECAPTCHA_SECRET_KEY",
    "INVITATION_JOIN_BASE_URL",
] as const;

export function loadAllowlistedSecrets(): Record<string, string> {
    if (!existsSync(DOCKER_ENV)) {
        return {};
    }
    const parsed = parse(readFileSync(DOCKER_ENV, "utf8"));
    const filtered: Record<string, string> = {};
    for (const key of SECRET_ALLOWLIST) {
        const value = parsed[key];
        if (value !== undefined) {
            filtered[key] = value;
        }
    }
    return filtered;
}

export function springDevEnv(): Record<string, string> {
    return {
        ...loadAllowlistedSecrets(),
        SPRING_PROFILES_ACTIVE: "dev",
    };
}
