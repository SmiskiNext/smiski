/**
 * Test double for the `@forge/api` invocation result.
 *
 * `invokeRemote` resolves to the platform's own response object, not a WHATWG
 * `Response`: the body is read through `text()`/`json()` and the headers expose
 * an `append`/`get`/`forEach` surface backed by `headers-utils`, not by the
 * browser's `Headers`. Building that shape here keeps every transport and
 * handler test asserting against the contract the platform actually delivers.
 */
import type { APIResponse, Headers as ForgeHeaders } from '@forge/api';

const JSON_HEADERS = { 'content-type': 'application/json' };

/**
 * Map-backed implementation of the platform's header surface. Deliberately not
 * a WHATWG `Headers`: that constructor rejects a name outside the RFC 7230
 * token grammar, which would make it impossible to reproduce the malformed
 * entry the adapter is expected to tolerate.
 */
function forgeHeaders(source: Record<string, string>): ForgeHeaders {
    const entries = new Map(
        Object.entries(source).map(([name, value]) => [
            name.toLowerCase(),
            value,
        ]),
    );

    return {
        append: (name, value) => entries.set(name.toLowerCase(), value),
        set: (name, value) => entries.set(name.toLowerCase(), value),
        delete: (name) => {
            entries.delete(name.toLowerCase());
        },
        get: (name) => entries.get(name.toLowerCase()) ?? null,
        has: (name) => entries.has(name.toLowerCase()),
        forEach: (callback) => {
            for (const [name, value] of entries) {
                callback(value, name);
            }
        },
    };
}

/**
 * Build an invocation result carrying a JSON body. A body of `undefined`
 * produces an empty representation, as the platform does for a status defined
 * to carry none.
 */
export function forgeResponse(
    status: number,
    body?: unknown,
    headers: Record<string, string> = JSON_HEADERS,
): APIResponse {
    const text = body === undefined ? '' : JSON.stringify(body);

    return {
        ok: status >= 200 && status <= 299,
        status,
        statusText: '',
        headers: forgeHeaders(headers),
        text: async () => text,
        json: async () => JSON.parse(text),
        arrayBuffer: async () => new ArrayBuffer(0),
    };
}
