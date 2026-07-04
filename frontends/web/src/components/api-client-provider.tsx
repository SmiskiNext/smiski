'use client';

import type { ReactNode } from 'react';
import { useEffect, useRef } from 'react';
import { configureApiClient, ejectApiClient } from '@/lib/api/client.ts';
import { webErrorTranslator } from '@/lib/api/error-translator.ts';

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!apiBaseUrl) {
    throw new Error(
        'NEXT_PUBLIC_API_BASE_URL is not set. '
            + 'Set this environment variable to the API gateway base URL before starting the application. '
            + 'See frontends/web/.env.local.example for the required variables.',
    );
}

type ApiClientProviderProps = {
    children: ReactNode;
};

/**
 * Installs the auth and JSend interceptors before any descendant effect runs.
 *
 * <p>Configuration happens during render rather than inside {@code useEffect}
 * so the interceptors are registered before child components fire their first
 * data-fetching effects. Children effects commit before parent effects, which
 * previously left the very first request after a hard reload without an
 * Authorization header.
 */
export function ApiClientProvider({ children }: ApiClientProviderProps) {
    const initialized = useRef(false);

    if (!initialized.current) {
        configureApiClient(apiBaseUrl, webErrorTranslator);
        initialized.current = true;
    }

    useEffect(() => {
        return () => {
            ejectApiClient();
            initialized.current = false;
        };
    }, []);

    return children;
}
