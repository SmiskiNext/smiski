/**
 * Bridges the generated `@smiskinext/sdks-jira` SDK transport to the Forge
 * Custom UI bridge's `requestJira`.
 *
 * The SDK is a `@hey-api` fetch client: each operation takes a custom
 * `fetch(Request)` and reads `.ok/.status/.headers.get()/.json()/.text()` off
 * the returned response. Here that custom fetch forwards the request through
 * `@forge/bridge`'s `requestJira(path, init)`, which runs the call as the
 * invoking user directly from the browser — no resolver hop needed (Forge
 * bridge v2+ supports calling Jira REST APIs from Custom UI natively). Only
 * the SDK's `Request` path + query are meaningful; `requestJira` resolves the
 * real Jira site origin itself, exactly like `issues.ts`/`projectMembers.ts`/
 * `currentUser.ts` already do for their own direct Jira calls.
 */
import { requestJira } from '@forge/bridge';
import type { Options } from '@smiskinext/sdks-jira';

/**
 * Placeholder origin for the SDK's `buildUrl`. Only `pathname + search` is
 * forwarded to `requestJira`, so this value never reaches the network.
 */
const PLACEHOLDER_BASE_URL = 'https://jira.invalid';

const jiraSdkFetch: typeof fetch = async (input, init) => {
    const request = new Request(input, init);
    const url = new URL(request.url);
    const routePath = `${url.pathname}${url.search}`;
    const body =
        request.method === 'GET' || request.method === 'HEAD'
            ? undefined
            : await request.text();

    const response = await requestJira(routePath, {
        method: request.method,
        headers: { Accept: 'application/json' },
        body,
    });

    return response as unknown as Response;
};

/** Shared per-call options binding every Jira SDK operation to the Forge bridge. */
export const jiraCallOptions = {
    baseUrl: PLACEHOLDER_BASE_URL,
    fetch: jiraSdkFetch,
} satisfies Partial<Options>;
