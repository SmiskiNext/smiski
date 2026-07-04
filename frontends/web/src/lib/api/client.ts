import { client } from '@/generated/client.gen.ts';
import { getAccessToken } from '@/lib/auth/cookies.ts';
import { handleUnauthorized } from '@/lib/auth/refresh.ts';
import { createJsendMiddleware } from './jsend-middleware.ts';
import type { ErrorTranslator } from './types.ts';

let authInterceptorId: number | null = null;
let unauthorizedInterceptorId: number | null = null;
let jsendInterceptorId: number | null = null;
let configuredBaseUrl = '';

async function attachAuthorizationHeader(request: Request): Promise<Request> {
    const token = await getAccessToken();
    if (!token) {
        return request;
    }
    const headers = new Headers(request.headers);
    headers.set('Authorization', `Bearer ${token}`);
    return new Request(request, { headers });
}

async function retryRequest(request: Request): Promise<Response> {
    return fetch(request);
}

/**
 * Configures the shared @hey-api client with the JSend unwrap middleware and
 * an Authorization header interceptor that reads the access_token cookie.
 *
 * Interceptors are registered and ejected symmetrically to guard against
 * duplicate registration on repeated calls.
 *
 * @param baseUrl    - API gateway base URL (defaults to empty string for relative URLs)
 * @param translator - optional i18n hook for error message translation
 */
export function configureApiClient(baseUrl = '', translator?: ErrorTranslator) {
    configuredBaseUrl = baseUrl;
    client.setConfig({ baseUrl });

    if (authInterceptorId !== null) {
        client.interceptors.request.eject(authInterceptorId);
    }
    authInterceptorId = client.interceptors.request.use(
        attachAuthorizationHeader,
    );

    if (unauthorizedInterceptorId !== null) {
        client.interceptors.response.eject(unauthorizedInterceptorId);
    }
    unauthorizedInterceptorId = client.interceptors.response.use(
        (response, request) =>
            handleUnauthorized(response, request, retryRequest),
    );

    if (jsendInterceptorId !== null) {
        client.interceptors.response.eject(jsendInterceptorId);
    }
    jsendInterceptorId = client.interceptors.response.use(
        createJsendMiddleware(translator),
    );
}

export function getApiBaseUrl(): string {
    return configuredBaseUrl;
}

/**
 * Ejects all registered interceptors, restoring the client to its default state.
 * Call this on provider unmount to prevent stale interceptors after remounts.
 */
export function ejectApiClient() {
    if (authInterceptorId !== null) {
        client.interceptors.request.eject(authInterceptorId);
        authInterceptorId = null;
    }
    if (unauthorizedInterceptorId !== null) {
        client.interceptors.response.eject(unauthorizedInterceptorId);
        unauthorizedInterceptorId = null;
    }
    if (jsendInterceptorId !== null) {
        client.interceptors.response.eject(jsendInterceptorId);
        jsendInterceptorId = null;
    }
}

export { client };
