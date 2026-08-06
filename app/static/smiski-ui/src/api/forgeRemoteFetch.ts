/**
 * Bridges the generated `@smiskinext/smiski-ts` SDK transport to Forge Remote.
 *
 * The `@hey-api/client-fetch` client resolves its transport as
 * `options.fetch ?? _config.fetch ?? globalThis.fetch` and invokes it with a
 * single WHATWG `Request`. Injecting this `fetch`-shaped adapter keeps the
 * SDK's typing, URL building, and zod validation while routing the actual call
 * through `invokeRemote`, which is proxied by the Forge platform. That proxy is
 * what attaches the app system token (`x-forge-oauth-system`) alongside the
 * Forge Invocation Token; `requestRemote` cannot, which is why it is not used
 * here. The app asserts no tenant/account identity headers of its own.
 *
 * `invokeRemote` differs from a `fetch` in three ways this adapter absorbs:
 * it takes no remote key (it resolves through the invoking module's
 * `resolver.endpoint`), it serializes the body itself, and it resolves to
 * `{ status, headers, body }` rather than a `Response`.
 *
 * The event streams are deliberately not routed here: Forge Remote buffers
 * response bodies and cannot deliver `text/event-stream`, so `api/sseClient.ts`
 * and `api/meetingEvents.ts` stay on raw `fetch`.
 */
import { invokeRemote } from '@forge/bridge';
import { createClient, createConfig } from '@smiskinext/smiski-ts';
import { getBackendContext } from './backendContext';

/**
 * Placeholder origin for the SDK's `buildUrl`. The adapter forwards only the
 * request `pathname + search` to `invokeRemote`, whose real origin is the
 * remote bound to the module's resolver endpoint, so this value never reaches
 * the network.
 */
const PLACEHOLDER_BASE_URL = 'https://forge-remote.invalid';

const ISSUE_ID_HEADER = 'x-issue-id';
const PROJECT_ID_HEADER = 'x-project-id';

type InvokeRemoteMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface InvokeRemoteResult {
    status?: number;
    statusText?: string;
    headers?: Record<string, string>;
    body?: unknown;
}

/**
 * Compose the outgoing headers: the SDK's own headers plus the Jira context
 * identifiers the gateway needs to scope its permission check. Caller-supplied
 * headers win, and an identifier that is absent omits its header rather than
 * sending a non-numeric value the gateway would reject.
 */
function composeHeaders(request: Request): Record<string, string> {
    const headers = Object.fromEntries(request.headers);
    const present = new Set(
        Object.keys(headers).map((name) => name.toLowerCase()),
    );
    const { issueId, projectId } = getBackendContext();

    if (issueId && !present.has(ISSUE_ID_HEADER)) {
        headers[ISSUE_ID_HEADER] = issueId;
    }
    if (projectId && !present.has(PROJECT_ID_HEADER)) {
        headers[PROJECT_ID_HEADER] = projectId;
    }

    return headers;
}

/**
 * Convert the SDK's serialized request body back into the object
 * `invokeRemote` expects, since Forge performs the `JSON.stringify` itself.
 * A request with no body omits it rather than sending an empty string.
 *
 * Every SDK operation serializes with `jsonBodySerializer`, so a body that
 * does not parse as JSON cannot be produced by the generated client. Were one
 * to appear, forwarding the raw text would have Forge stringify it a second
 * time, so the body is omitted instead of sent double-encoded.
 */
function parseBody(bodyText: string): unknown {
    if (bodyText === '') return undefined;
    try {
        return JSON.parse(bodyText);
    } catch {
        return undefined;
    }
}

/**
 * Whether a status can be carried by a reconstructed `Response`. The `Response`
 * constructor rejects anything outside 200-599 with a `RangeError`, so an
 * out-of-range status is a failure rather than a response to rebuild.
 */
function isUsableStatus(status: unknown): status is number {
    return typeof status === 'number' && status >= 200 && status <= 599;
}

/**
 * Unwrap the `{ body, metadata }` envelope the bridge's `InvokeResponse` union
 * allows. The installed pre-release's `invoke-endpoint.js` returns the bare
 * response, so only `invoke.js` produces the envelope today. Unwrapping it
 * regardless keeps a future envelope from reading as a status-less failure.
 *
 * `metadata` is optional in that union, so its presence cannot discriminate an
 * envelope — `{ body }` alone is a valid one. The envelope is recognised
 * instead by the outer value carrying no usable status while its `body` carries
 * one. A bare response is therefore never unwrapped: its own status matches
 * first, even when its payload is a Problem Details body whose `status` member
 * would otherwise look like an envelope's.
 */
function unwrapMetadataEnvelope(
    result: InvokeRemoteResult,
): InvokeRemoteResult {
    if (isUsableStatus(result?.status)) return result;

    const enveloped = (result as { body?: unknown }).body;
    if (
        typeof enveloped === 'object'
        && enveloped !== null
        && isUsableStatus((enveloped as InvokeRemoteResult).status)
    ) {
        return enveloped as InvokeRemoteResult;
    }
    return result;
}

/**
 * Detect a failed invocation reported as a resolved value. The installed
 * `@forge/bridge` pre-release returns `{ ...(success ? payload : error) }`,
 * so an error can arrive as a rejection or as a resolved payload carrying no
 * usable status; both are treated as failures.
 */
function failureMessage(result: InvokeRemoteResult): string | undefined {
    if (isUsableStatus(result?.status)) return undefined;

    const candidate = result as { message?: unknown; error?: unknown };
    if (typeof candidate.message === 'string' && candidate.message !== '') {
        return candidate.message;
    }
    if (typeof candidate.error === 'string' && candidate.error !== '') {
        return candidate.error;
    }
    return 'The meeting backend could not be reached.';
}

/**
 * Serialize the invocation body back to text for the reconstructed `Response`,
 * because the SDK's `parseAs: 'json'` branch reads `text()` and parses it.
 */
function serializeBody(body: unknown): string | null {
    if (body === undefined || body === null) return null;
    if (typeof body === 'string') return body;
    return JSON.stringify(body);
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
 * constructor rejects. A single malformed entry from the platform would
 * otherwise throw a bare `TypeError: invalid header name` out of the adapter,
 * bypassing the readable-failure path the calling screens render. Such an entry
 * is dropped instead, so the response still reaches the SDK with its status,
 * its remaining headers, and its body.
 */
function toHeaders(source: Record<string, string> | undefined): Headers {
    const headers = new Headers();
    for (const [name, value] of Object.entries(source ?? {})) {
        if (isUsableHeader(name, value)) {
            headers.set(name, value);
        }
    }
    return headers;
}

/**
 * Rebuild a WHATWG `Response` from the invocation result so the SDK client can
 * keep reading `ok`, `status`, `headers`, and `text()`. A status defined to
 * carry no representation yields an empty body, which the SDK's `204` branch
 * resolves as an empty object.
 *
 * The platform's `content-length` measures the bytes it received, while this
 * body is re-serialized from the parsed value, so it is dropped rather than
 * allowed to contradict the body. The SDK treats `Content-Length: 0` as an
 * empty representation, which a stale header would trigger on a populated one.
 */
function toResponse(result: InvokeRemoteResult): Response {
    const status = result.status as number;
    const headers = toHeaders(result.headers);
    headers.delete('Content-Length');
    if (!headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }

    const isBodyless = status === 204 || status === 205 || status === 304;

    return new Response(isBodyless ? null : serializeBody(result.body), {
        status,
        statusText: result.statusText,
        headers,
    });
}

/**
 * Translate an SDK-issued `Request` into an `invokeRemote` call. Reads the
 * `pathname + search` (the SDK-built path such as `/api/1/meetings:instant`),
 * the method, and the headers, and returns a reconstructed `Response` the SDK
 * then parses and validates.
 *
 * A result the `Response` constructor cannot accept is reported through the
 * same readable-failure path as an unreachable remote, so no raw `TypeError`
 * from the reconstruction escapes to the calling screen.
 */
export async function forgeRemoteFetch(
    input: RequestInfo | URL,
    init?: RequestInit,
): Promise<Response> {
    const request = input instanceof Request ? input : new Request(input, init);
    const url = new URL(request.url);
    const bodyText = await request.text();
    const body = parseBody(bodyText);

    const result = unwrapMetadataEnvelope(
        ((await invokeRemote({
            path: `${url.pathname}${url.search}`,
            method: request.method as InvokeRemoteMethod,
            headers: composeHeaders(request),
            ...(body === undefined ? {} : { body }),
        })) ?? {}) as InvokeRemoteResult,
    );

    const failure = failureMessage(result);
    if (failure !== undefined) {
        throw new Error(failure);
    }

    try {
        return toResponse(result);
    } catch {
        throw new Error(
            'The meeting backend returned a response that could not be read.',
        );
    }
}

/**
 * Shared SDK client whose transport is the Forge Remote adapter. Passed to
 * every SDK operation via the `client` option so the whole app funnels backend
 * calls through the platform-proxied, system-token-bearing invocation.
 */
export const forgeRemoteClient = createClient(
    createConfig({
        baseUrl: PLACEHOLDER_BASE_URL,
        fetch: forgeRemoteFetch,
    }),
);
