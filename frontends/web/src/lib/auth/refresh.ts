import { getApiBaseUrl } from '@/lib/api/client.ts';
import {
    clearAuthCookies,
    getRefreshToken,
    setAccessToken,
    setRefreshToken,
} from '@/lib/auth/cookies.ts';
import { clearRememberMe, getRememberMe } from '@/lib/auth/remember-me.ts';

const REFRESH_PATH = '/api/v1/auth/refresh';
const UNAUTHORIZED_SKIP_PATHS = [
    '/api/v1/auth/login',
    '/api/v1/auth/register',
    '/api/v1/auth/google-login',
    '/api/v1/auth/forgot-password',
    '/api/v1/auth/reset-password',
    '/api/v1/auth/verify-otp',
    REFRESH_PATH,
    '/api/v1/auth/logout',
    '/api/v1/meetings/invite-tokens:validate',
    ':requestJoin',
];

type RefreshTokens = {
    accessToken: string;
    refreshToken: string;
};

type RefreshEnvelope = {
    status?: string;
    data?: Partial<RefreshTokens>;
};

type RetryRequest = (request: Request) => Promise<Response>;

let refreshPromise: Promise<RefreshTokens> | null = null;

export function shouldSkipUnauthorizedRefresh(url: string): boolean {
    const pathname = getUrlPathname(url);
    return UNAUTHORIZED_SKIP_PATHS.some((path) => pathname.endsWith(path));
}

export async function refreshAccessToken(): Promise<RefreshTokens> {
    if (!refreshPromise) {
        refreshPromise = requestRefreshToken().finally(() => {
            refreshPromise = null;
        });
    }
    return refreshPromise;
}

export async function handleUnauthorized(
    response: Response,
    request: Request,
    retryFn: RetryRequest,
): Promise<Response> {
    if (response.status !== 401 || shouldSkipUnauthorizedRefresh(request.url)) {
        return response;
    }

    try {
        const tokens = await refreshAccessToken();
        const headers = new Headers(request.headers);
        headers.set('Authorization', `Bearer ${tokens.accessToken}`);
        return retryFn(new Request(request, { headers }));
    } catch {
        await clearExpiredSession();
        redirectToLogin();
        return response;
    }
}

async function requestRefreshToken(): Promise<RefreshTokens> {
    const refreshToken = await getRefreshToken();
    if (!refreshToken) {
        throw new Error('Missing refresh token');
    }

    const response = await fetch(`${getApiBaseUrl()}${REFRESH_PATH}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
        throw new Error('Refresh token request failed');
    }

    const envelope = (await response.json()) as RefreshEnvelope;
    const accessToken = envelope.data?.accessToken;
    const rotatedRefreshToken = envelope.data?.refreshToken;
    if (envelope.status !== 'success' || !accessToken || !rotatedRefreshToken) {
        throw new Error('Invalid refresh token response');
    }

    const rememberMe = getRememberMe();
    await setAccessToken(accessToken, rememberMe);
    await setRefreshToken(rotatedRefreshToken, rememberMe);

    return { accessToken, refreshToken: rotatedRefreshToken };
}

async function clearExpiredSession(): Promise<void> {
    await clearAuthCookies();
    clearRememberMe();
}

function redirectToLogin(): void {
    if (typeof window === 'undefined') {
        return;
    }
    window.location.assign(`/${getCurrentLocale()}/login`);
}

function getCurrentLocale(): string {
    if (typeof window === 'undefined') {
        return 'en';
    }
    const firstSegment = window.location.pathname.split('/').filter(Boolean)[0];
    return firstSegment && /^[a-z]{2}(?:-[A-Z]{2})?$/.test(firstSegment)
        ? firstSegment
        : 'en';
}

function getUrlPathname(url: string): string {
    if (typeof window !== 'undefined') {
        return new URL(url, window.location.origin).pathname;
    }
    return new URL(url, 'http://localhost').pathname;
}
