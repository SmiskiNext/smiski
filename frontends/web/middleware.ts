import { NextRequest, NextResponse } from "next/server";
import createIntlMiddleware from "next-intl/middleware";
import { routing } from "./src/i18n/request";

const intlMiddleware = createIntlMiddleware(routing);

const PROTECTED_PREFIX = "/workspace";
const WORKSPACE_PUBLIC_PATHS = new Set(["/workspace/meeting-room"]);

function stripLocale(pathname: string): string {
    for (const locale of routing.locales) {
        const prefix = `/${locale}`;
        if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
            return pathname.slice(prefix.length) || "/";
        }
    }
    return pathname;
}

function extractLocale(pathname: string): string {
    for (const locale of routing.locales) {
        const prefix = `/${locale}`;
        if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
            return locale;
        }
    }
    return routing.defaultLocale;
}

export default function middleware(request: NextRequest) {
    const { pathname } = request.nextUrl;
    const strippedPath = stripLocale(pathname);

    if (
        strippedPath.startsWith(PROTECTED_PREFIX) &&
        !WORKSPACE_PUBLIC_PATHS.has(strippedPath)
    ) {
        const accessToken = request.cookies.get("access_token")?.value;
        if (!accessToken) {
            const locale = extractLocale(pathname);
            const loginUrl = new URL(`/${locale}/login`, request.url);
            return NextResponse.redirect(loginUrl);
        }
    }

    return intlMiddleware(request);
}

export const config = {
    matcher: ["/", "/(vi|en)/:path*", "/((?!_next|_vercel|.*\\..*).*)" ],
};
