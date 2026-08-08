/**
 * Opportunistic `ETag` revalidation for the Jira reads this app repeats.
 *
 * A cached permission check still has to be revalidated once its `staleTime`
 * lapses. Jira answers those reads with an `ETag`, so the revalidation can be
 * conditional: send `If-None-Match`, and a `304` confirms the previous answer
 * without a body to parse.
 *
 * Every part of that is best-effort. Jira may send no `ETag`; the Forge bridge
 * may drop `If-None-Match` on the way out; a `304` may simply never arrive. In
 * each case this degrades to the plain read it wraps — no extra request, no
 * different result. Nothing here is load-bearing, so nothing here may fail
 * loudly.
 *
 * What is stored is the *derived* value, not the response body. The permission
 * listing this backs is a site-wide catalogue of every Jira permission, of which
 * this app needs two keys; keeping the two keys instead of the catalogue is the
 * difference between bytes and tens of kilobytes in a shared
 * {@link Storage} quota. A `304` means the representation did not change, so a
 * value derived from it did not either.
 *
 * Deliberately not a general HTTP cache. It covers named resources that opt in
 * by calling {@link revalidatedJiraRead}, and knows nothing about
 * `Cache-Control`, `Last-Modified`, or any method other than a read.
 */

import { BUILD_VERSION } from '../utils/buildVersion';

const STORAGE_KEY_PREFIX = 'smiski:jira-etag:';

const NOT_MODIFIED = 304;

interface StoredValidator<TValue> {
    buildVersion: string;
    etag: string;
    value: TValue;
}

/**
 * The subset of a `@hey-api` result this module reads.
 *
 * A `304` is not `response.ok`, so the SDK client reports it through `error`
 * while still handing back the `response` it came from — which is the only
 * reason the status can be recovered here at all.
 */
interface ConditionalReadResult<TData> {
    data?: TData;
    error?: unknown;
    response?: Response;
}

/** Outcome of a conditional read, in the shape the calling adapter reports. */
export interface RevalidatedRead<TValue> {
    /** Present when the read settled, whether from Jira or from a `304`. */
    value?: TValue;
    /** Present when the read failed for any reason other than a `304`. */
    error?: unknown;
    /** The response the failure came from, for the caller's error message. */
    response?: Response;
}

function storageKey(resource: string): string {
    return `${STORAGE_KEY_PREFIX}${resource}`;
}

/**
 * Reads a stored validator, ignoring one written by a different bundle.
 *
 * A derived value's shape is decided by the code that derived it, so a
 * validator from another build cannot be assumed to still describe this one.
 */
function readValidator<TValue>(
    resource: string,
): StoredValidator<TValue> | undefined {
    try {
        const raw = localStorage.getItem(storageKey(resource));
        if (!raw) return undefined;
        const stored = JSON.parse(raw) as StoredValidator<TValue>;
        if (stored.buildVersion !== BUILD_VERSION) return undefined;
        if (typeof stored.etag !== 'string' || stored.etag === '') {
            return undefined;
        }
        return stored;
    } catch {
        return undefined;
    }
}

function writeValidator<TValue>(
    resource: string,
    etag: string,
    value: TValue,
): void {
    try {
        localStorage.setItem(
            storageKey(resource),
            JSON.stringify({ buildVersion: BUILD_VERSION, etag, value }),
        );
    } catch {
        return;
    }
}

/**
 * Reads the response `ETag`, tolerating a response object without `headers`.
 *
 * `requestJira` resolves to a `Response`, but this runs on whatever the bridge
 * hands back, so the header bag is probed rather than assumed.
 */
function readETag(response: Response | undefined): string | undefined {
    try {
        const etag = response?.headers?.get('ETag');
        return etag ?? undefined;
    } catch {
        return undefined;
    }
}

/**
 * Performs a Jira read that revalidates against a stored `ETag` when it can.
 *
 * `resource` names the cache entry and must distinguish everything that could
 * change the answer — including the invoking user, for a read whose answer is
 * user-specific. `read` receives the conditional headers to pass to the SDK
 * operation, empty when there is nothing to revalidate against. `derive`
 * converts the response payload into the small value worth storing, and may
 * throw to reject a payload it cannot interpret.
 *
 * A `304` returns the value captured before the request, so an entry evicted
 * from storage mid-flight cannot turn into a missing value.
 */
export async function revalidatedJiraRead<TData, TValue>(
    resource: string,
    read: (
        headers: Record<string, string>,
    ) => Promise<ConditionalReadResult<TData>>,
    derive: (data: TData | undefined) => TValue,
): Promise<RevalidatedRead<TValue>> {
    const stored = readValidator<TValue>(resource);
    const result = await read(
        stored ? { 'If-None-Match': stored.etag } : {},
    ).catch((error: unknown) => ({ error }) as ConditionalReadResult<TData>);

    if (stored && result.response?.status === NOT_MODIFIED) {
        return { value: stored.value };
    }

    if (result.error) {
        return { error: result.error, response: result.response };
    }

    let value: TValue;
    try {
        value = derive(result.data);
    } catch (error) {
        return { error, response: result.response };
    }

    const etag = readETag(result.response);
    if (etag) {
        writeValidator(resource, etag, value);
    }

    return { value };
}
