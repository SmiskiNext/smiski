/**
 * Bridges the generated `@smiskinext/smiski-ts` SDK transport to Forge Remote
 * from inside a Forge **function**.
 *
 * This is the server-side analog of
 * `static/smiski-ui/src/api/forgeRemoteFetch.ts`. Lifecycle events have no
 * iframe and no browser, so `@forge/bridge` cannot serve them; `@forge/api`
 * `invokeRemote` is the function-side equivalent, and Atlassian attaches the
 * signed Forge Invocation Token (FIT) to it. The remote is addressed by key
 * because a function resolves no `resolver.endpoint`.
 *
 * The `@hey-api/client-fetch` client resolves its transport as
 * `options.fetch ?? _config.fetch ?? globalThis.fetch` and invokes it with a
 * single WHATWG `Request`. Injecting this `fetch`-shaped adapter keeps the
 * SDK's typing, URL building, and zod validation while routing the call
 * through the Forge platform proxy.
 *
 * Unlike the browser transport, this adapter injects **no** `x-issue-id` /
 * `x-project-id` context headers: a lifecycle event has no issue or project
 * scope, and sending them would push the gateway onto the Jira
 * permission-check path. It asserts no tenant/account identity either — the
 * gateway derives both from the FIT.
 *
 * `invokeRemote` differs from a `fetch` in three ways this adapter absorbs: it
 * takes the remote key as its first argument, it accepts the body as already
 * serialized text, and it resolves to a Forge response object rather than a
 * WHATWG `Response`.
 */
import { type APIResponse, invokeRemote } from '@forge/api';
import { createClient, createConfig } from '@smiskinext/smiski-ts';

/**
 * The `remotes` entry for the `meet` backend in `manifest.yml`, which already
 * declares `operations: [compute]` — the prerequisite for `invokeRemote`.
 */
const REMOTE_KEY = 'meet-backend';

/**
 * Placeholder origin for the SDK's `buildUrl`. The adapter forwards only the
 * request `pathname + search` to `invokeRemote`, whose real origin is the
 * remote's `baseUrl`, so this value never reaches the network.
 */
const PLACEHOLDER_BASE_URL = 'https://forge-remote.invalid';

const RESPONSE_UNREADABLE =
    'The meeting backend returned a response that could not be read.';

/**
 * Statuses defined to carry no representation. The `Response` constructor
 * rejects a non-null body for these, so the body is dropped rather than
 * forwarded.
 */
const BODYLESS_STATUSES = new Set([204, 205, 304]);

/**
 * Forward the SDK's own headers verbatim. No context or identity header is
 * added: the FIT the platform attaches is the only identity on the request.
 */
function toHeaderRecord(request: Request): Record<string, string> {
    return Object.fromEntries(request.headers);
}

/**
 * Read the SDK's already-serialized body. `invokeRemote` takes the body as
 * text, so it is forwarded unchanged rather than parsed; a request with no
 * body omits it rather than sending an empty string.
 */
async function readBody(request: Request): Promise<string | undefined> {
    const bodyText = await request.text();
    return bodyText === '' ? undefined : bodyText;
}

/**
 * Whether a name/value pair is one the `Headers` constructor accepts. Probing
 * on a throwaway instance keeps the validation rules the platform's, rather
 * than restating the RFC 7230 token grammar here.
 */
function isUsableHeader(name: string, value: string): boolean {
    try {
        new Headers().set(name, value);
        return true;
    } catch {
        return false;
    }
}

/**
 * Build the response headers, tolerating a name or value the `Headers`
 * constructor rejects. A single malformed entry would otherwise fail the whole
 * reconstruction and cost a spurious lifecycle retry; it is dropped instead,
 * so the response still reaches the SDK with its status, its remaining
 * headers, and its body.
 */
function toHeaders(source: APIResponse['headers']): Headers {
    const headers = new Headers();
    source.forEach((value, name) => {
        if (isUsableHeader(name, value)) {
            headers.set(name, value);
        }
    });
    return headers;
}

/**
 * Rebuild a WHATWG `Response` from the invocation result so the SDK client can
 * keep reading `ok`, `status`, `headers`, and `text()`.
 *
 * `Content-Length` is forwarded untouched, unlike in the browser transport:
 * the body is passed through as the same text the platform received rather
 * than re-serialized from a parsed value, so the header cannot contradict it.
 *
 * A missing `Content-Type` defaults to JSON, because the SDK resolves an
 * absent content type to its `stream` branch and would skip response
 * validation.
 */
async function toResponse(result: APIResponse): Promise<Response> {
    const headers = toHeaders(result.headers);
    if (!headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }

    const body = BODYLESS_STATUSES.has(result.status)
        ? null
        : await result.text();

    return new Response(body, {
        status: result.status,
        statusText: result.statusText,
        headers,
    });
}

/**
 * Translate an SDK-issued `Request` into an `invokeRemote` call. Reads the
 * `pathname + search` (the SDK-built path such as `/api/1/tenants`), the
 * method, and the headers, and returns a reconstructed `Response` the SDK then
 * parses and validates.
 *
 * A platform-level invocation failure rejects out of `invokeRemote` and is
 * left to propagate: the SDK client turns it into a result whose `error` is
 * present, which is what the lifecycle handlers act on. A result the
 * `Response` constructor cannot accept is reported the same way, so no raw
 * `RangeError` or `TypeError` from the reconstruction escapes as an opaque
 * failure.
 */
export async function forgeApiRemoteFetch(
    input: RequestInfo | URL,
    init?: RequestInit,
): Promise<Response> {
    const request = input instanceof Request ? input : new Request(input, init);
    const url = new URL(request.url);
    const body = await readBody(request);

    const result = await invokeRemote(REMOTE_KEY, {
        path: `${url.pathname}${url.search}`,
        method: request.method,
        headers: toHeaderRecord(request),
        ...(body === undefined ? {} : { body }),
    });

    try {
        return await toResponse(result);
    } catch {
        throw new Error(RESPONSE_UNREADABLE);
    }
}

/**
 * SDK client whose transport is the Forge Remote adapter. Passed to every SDK
 * operation the lifecycle handlers call via the `client` option, so they reach
 * the backend through the platform-proxied, FIT-bearing invocation.
 */
export const forgeApiRemoteClient = createClient(
    createConfig({
        baseUrl: PLACEHOLDER_BASE_URL,
        fetch: forgeApiRemoteFetch,
    }),
);
