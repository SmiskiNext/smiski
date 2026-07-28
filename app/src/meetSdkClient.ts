/**
 * Smiski `meet` backend access for the resolver, via `@smiskinext/smiski-ts`.
 *
 * The SDK is a `@hey-api` fetch client. Here it runs inside the Forge function
 * and forwards to the Caddy gateway (`SMISKI_API_BASE_URL`) with the tenant and
 * account identity taken from the per-invocation resolver context. Because that
 * identity is request-scoped, the client is constructed per request (closing
 * over the context values) rather than as a module-level singleton.
 */
import { createClient, createConfig } from '@smiskinext/smiski-ts';

/** The SDK client instance type (not re-exported by the package top level). */
export type MeetClient = ReturnType<typeof createClient>;

/** Identity derived from the Forge invocation context for backend headers. */
export interface MeetClientIdentity {
    /** Jira `cloudId`, forwarded as `X-Tenant-ID`. */
    tenantId: string;
    /** Jira `accountId`, forwarded as `X-Account-Id`. */
    accountId: string;
}

function resolveBaseUrl(): string {
    const baseUrl = process.env.SMISKI_API_BASE_URL;
    if (!baseUrl) {
        throw new Error(
            'SMISKI_API_BASE_URL is not configured for the resolver.',
        );
    }
    return baseUrl;
}

/**
 * Build a `meet` client bound to a single invocation's identity. The custom
 * fetch attaches the tenant/account headers server-side so the browser never
 * supplies them.
 */
export function createMeetClient(identity: MeetClientIdentity): MeetClient {
    const identityFetch: typeof fetch = (input, init) => {
        const request = new Request(input, init);
        request.headers.set('X-Tenant-ID', identity.tenantId);
        request.headers.set('X-Account-Id', identity.accountId);
        request.headers.set('Accept', 'application/json');
        request.headers.set('Content-Type', 'application/json');
        return fetch(request);
    };

    return createClient(
        createConfig({
            baseUrl: resolveBaseUrl(),
            fetch: identityFetch,
        }),
    );
}
