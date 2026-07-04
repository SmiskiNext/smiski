import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/lib/api/client.ts', () => ({
    getApiBaseUrl: () => 'https://api.example.test',
}));

vi.mock('@/lib/auth/cookies.ts', () => ({
    clearAuthCookies: vi.fn(),
    getRefreshToken: vi.fn(),
    setAccessToken: vi.fn(),
    setRefreshToken: vi.fn(),
}));

import {
    clearAuthCookies,
    getRefreshToken,
    setAccessToken,
    setRefreshToken,
} from '@/lib/auth/cookies.ts';
import { getRememberMe, setRememberMe } from '@/lib/auth/remember-me.ts';
import {
    handleUnauthorized,
    refreshAccessToken,
    shouldSkipUnauthorizedRefresh,
} from './refresh.ts';

const mockedClearAuthCookies = vi.mocked(clearAuthCookies);
const mockedGetRefreshToken = vi.mocked(getRefreshToken);
const mockedSetAccessToken = vi.mocked(setAccessToken);
const mockedSetRefreshToken = vi.mocked(setRefreshToken);

function createWindowStub(pathname = '/en/workspace') {
    const storage = new Map<string, string>();
    return {
        localStorage: {
            clear: vi.fn(() => storage.clear()),
            getItem: vi.fn((key: string) => storage.get(key) ?? null),
            removeItem: vi.fn((key: string) => storage.delete(key)),
            setItem: vi.fn((key: string, value: string) =>
                storage.set(key, value),
            ),
        },
        location: {
            assign: vi.fn(),
            origin: 'https://web.example.test',
            pathname,
        },
    };
}

function unauthorizedResponse() {
    return new Response(JSON.stringify({ status: 'fail' }), { status: 401 });
}

function refreshSuccessResponse(
    accessToken = 'access-new',
    refreshToken = 'refresh-new',
) {
    return new Response(
        JSON.stringify({
            status: 'success',
            data: { accessToken, refreshToken, expiresIn: 900 },
        }),
        { status: 200, headers: { 'content-type': 'application/json' } },
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
    vi.stubGlobal('window', createWindowStub());
    mockedGetRefreshToken.mockResolvedValue('refresh-old');
    globalThis.fetch = vi.fn().mockResolvedValue(refreshSuccessResponse());
});

describe('refreshAccessToken', () => {
    it('shares one refresh request across concurrent callers', async () => {
        await Promise.all([refreshAccessToken(), refreshAccessToken()]);

        expect(fetch).toHaveBeenCalledTimes(1);
        expect(fetch).toHaveBeenCalledWith(
            'https://api.example.test/api/v1/auth/refresh',
            expect.objectContaining({ method: 'POST' }),
        );
    });

    it('updates rotated token cookies with remembered persistence', async () => {
        setRememberMe(true);

        await refreshAccessToken();

        expect(mockedSetAccessToken).toHaveBeenCalledWith('access-new', true);
        expect(mockedSetRefreshToken).toHaveBeenCalledWith('refresh-new', true);
    });
});

describe('handleUnauthorized', () => {
    it('retries the original request with the rotated access token', async () => {
        const retryResponse = new Response(JSON.stringify({ ok: true }), {
            status: 200,
        });
        const retry = vi.fn().mockResolvedValue(retryResponse);

        const response = await handleUnauthorized(
            unauthorizedResponse(),
            new Request('https://api.example.test/api/v1/meetings'),
            retry,
        );

        const retriedRequest = retry.mock.calls[0][0] as Request;
        expect(response).toBe(retryResponse);
        expect(retry).toHaveBeenCalledTimes(1);
        expect(retriedRequest.headers.get('Authorization')).toBe(
            'Bearer access-new',
        );
    });

    it('clears session state and redirects when refresh fails', async () => {
        globalThis.fetch = vi
            .fn()
            .mockResolvedValue(new Response(null, { status: 401 }));
        const windowStub = createWindowStub('/vi/workspace');
        vi.stubGlobal('window', windowStub);

        const originalResponse = unauthorizedResponse();
        const response = await handleUnauthorized(
            originalResponse,
            new Request('https://api.example.test/api/v1/meetings'),
            vi.fn(),
        );

        expect(response).toBe(originalResponse);
        expect(mockedClearAuthCookies).toHaveBeenCalledTimes(1);
        expect(getRememberMe()).toBe(false);
        expect(windowStub.location.assign).toHaveBeenCalledWith('/vi/login');
    });

    it('does not refresh URLs from the unauthorized skip list', async () => {
        const retry = vi.fn();
        const response = await handleUnauthorized(
            unauthorizedResponse(),
            new Request('https://api.example.test/api/v1/auth/login'),
            retry,
        );

        expect(response.status).toBe(401);
        expect(fetch).not.toHaveBeenCalled();
        expect(retry).not.toHaveBeenCalled();
        expect(
            shouldSkipUnauthorizedRefresh(
                'https://api.example.test/gateway/api/v1/auth/refresh',
            ),
        ).toBe(true);
    });

    it('does not crash during SSR cleanup', async () => {
        globalThis.fetch = vi
            .fn()
            .mockResolvedValue(new Response(null, { status: 401 }));
        vi.stubGlobal('window', undefined);

        await expect(
            handleUnauthorized(
                unauthorizedResponse(),
                new Request('https://api.example.test/api/v1/meetings'),
                vi.fn(),
            ),
        ).resolves.toBeInstanceOf(Response);
    });
});
