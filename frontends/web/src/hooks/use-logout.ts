'use client';

import { useCallback, useState } from 'react';
import { logout as logoutRequest } from '@/generated/sdk.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import { clearAuthCookies } from '@/lib/auth/cookies.ts';
import { clearRememberMe } from '@/lib/auth/remember-me.ts';

const REFRESH_TOKEN_KEY = 'refresh_token';

export type LogoutPhase = 'IDLE' | 'LOGGING_OUT' | 'ERROR';

function readCookieValue(name: string): string | undefined {
    if (typeof document === 'undefined') return undefined;
    const match = document.cookie
        .split('; ')
        .find((row) => row.startsWith(`${name}=`));
    if (!match) return undefined;
    return match.split('=').slice(1).join('=');
}

export function useLogout() {
    const [logoutState, setLogoutState] = useState<LogoutPhase>('IDLE');
    const [logoutErrorMessage, setLogoutErrorMessage] = useState<string | null>(
        null,
    );

    const logout = useCallback(
        async (locale: string, errorFallback: string) => {
            setLogoutState('LOGGING_OUT');
            setLogoutErrorMessage(null);

            try {
                const refreshToken = readCookieValue(REFRESH_TOKEN_KEY);
                if (refreshToken) {
                    await logoutRequest({
                        body: { refreshToken },
                        throwOnError: false,
                    });
                }
                await clearAuthCookies();
                clearRememberMe();
                setLogoutState('IDLE');
                window.location.href = `/${locale}/login`;
            } catch (err) {
                let message = errorFallback;
                if (err instanceof ApiFailError || err instanceof ApiError) {
                    message = err.message;
                }
                setLogoutErrorMessage(message);
                setLogoutState('ERROR');
            }
        },
        [],
    );

    return { logoutState, logoutErrorMessage, logout };
}
