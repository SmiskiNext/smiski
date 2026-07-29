/**
 * Bridges the generated `@smiskinext/smiski-ts` SDK transport to Forge Remote.
 *
 * The `@hey-api/client-fetch` client resolves its transport as
 * `options.fetch ?? _config.fetch ?? globalThis.fetch` and invokes it with a
 * single WHATWG `Request`. Injecting this `fetch`-shaped adapter keeps the
 * SDK's typing, URL building, and zod validation while routing the actual call
 * through `requestRemote('meet-backend', ...)`, so Atlassian attaches the Forge
 * Invocation Token (FIT) as `Authorization: Bearer`. The app asserts no
 * tenant/account identity headers of its own.
 */
import { requestRemote } from '@forge/bridge';
import { createClient, createConfig } from '@smiskinext/smiski-ts';

/** Manifest `remotes` key for the `meet` backend (see app/manifest.yml). */
const MEET_REMOTE_KEY = 'meet-backend';

/**
 * Placeholder origin for the SDK's `buildUrl`. The adapter forwards only the
 * request `pathname + search` to `requestRemote`, whose real origin is the
 * Forge remote binding, so this value never reaches the network.
 */
const PLACEHOLDER_BASE_URL = 'https://forge-remote.invalid';

/**
 * Translate a SDK-issued `Request` into a `requestRemote` call. Reads the
 * `pathname + search` (the SDK-built path such as `/api/1/meetings:instant`),
 * the method, and the headers, and forwards the text body verbatim so the
 * serialized JSON is preserved. Returns the Response-compatible result
 * `requestRemote` already yields, which the SDK then parses and validates.
 */
export async function forgeRemoteFetch(
    input: RequestInfo | URL,
    init?: RequestInit,
): Promise<Response> {
    const request = input instanceof Request ? input : new Request(input, init);
    const url = new URL(request.url);
    const path = `${url.pathname}${url.search}`;
    const bodyText = await request.text();

    return requestRemote(MEET_REMOTE_KEY, {
        path,
        method: request.method,
        headers: Object.fromEntries(request.headers),
        body: bodyText === '' ? undefined : bodyText,
    }) as Promise<Response>;
}

/**
 * Shared SDK client whose transport is the Forge Remote adapter. Passed to
 * every SDK operation via the `client` option so the whole app funnels backend
 * calls through the FIT-bearing Forge Remote binding.
 */
export const forgeRemoteClient = createClient(
    createConfig({
        baseUrl: PLACEHOLDER_BASE_URL,
        fetch: forgeRemoteFetch,
    }),
);
