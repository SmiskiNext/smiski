const ACCESS_TOKEN_KEY = 'access_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
const MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

function getCookieOptions(rememberMe: boolean) {
    const secure = process.env.NODE_ENV === 'production';
    return {
        path: '/',
        sameSite: 'lax' as const,
        ...(rememberMe ? { maxAge: MAX_AGE_SECONDS } : {}),
        secure,
    };
}

export async function setAuthCookies(
    accessToken: string,
    refreshToken: string,
    rememberMe: boolean,
): Promise<void> {
    const options = getCookieOptions(rememberMe);

    await cookieStore.set({
        name: ACCESS_TOKEN_KEY,
        value: accessToken,
        ...options,
    });
    await cookieStore.set({
        name: REFRESH_TOKEN_KEY,
        value: refreshToken,
        ...options,
    });
}

export async function setAccessToken(
    value: string,
    rememberMe: boolean,
): Promise<void> {
    await cookieStore.set({
        name: ACCESS_TOKEN_KEY,
        value,
        ...getCookieOptions(rememberMe),
    });
}

export async function setRefreshToken(
    value: string,
    rememberMe: boolean,
): Promise<void> {
    await cookieStore.set({
        name: REFRESH_TOKEN_KEY,
        value,
        ...getCookieOptions(rememberMe),
    });
}

export async function clearAuthCookies(): Promise<void> {
    await cookieStore.delete({ name: ACCESS_TOKEN_KEY, path: '/' });
    await cookieStore.delete({ name: REFRESH_TOKEN_KEY, path: '/' });
}

export async function getAccessToken(): Promise<string | undefined> {
    const cookie = await cookieStore.get(ACCESS_TOKEN_KEY);
    return cookie?.value;
}

export async function getRefreshToken(): Promise<string | undefined> {
    const cookie = await cookieStore.get(REFRESH_TOKEN_KEY);
    return cookie?.value;
}
