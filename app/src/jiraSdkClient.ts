/**
 * Jira Cloud REST access for the resolver, via `@smiskinext/sdks-jira`.
 *
 * The SDK is a `@hey-api` fetch client: each operation takes a custom
 * `fetch(Request)` and reads `.ok/.status/.headers.get()/.json()/.text()` off
 * the returned response. Here that custom fetch forwards the request through
 * `@forge/api` `asUser().requestJira(...)`, so Jira enforces the invoking
 * user's own "Browse users" permission. Both the SDK's `Request` and Forge's
 * response satisfy the shapes the client expects, so no manual re-wrapping is
 * needed beyond translating the URL into a Forge route.
 */
import api, { assumeTrustedRoute } from '@forge/api';
import {
    findUsers,
    getAllUsers,
    type Options,
    type User,
} from '@smiskinext/sdks-jira';
import { toWorkspaceUsers, type WorkspaceUser } from './workspaceUserMapping';

export type { WorkspaceUser } from './workspaceUserMapping';

const USER_SEARCH_MAX_RESULTS = 50;

/**
 * Custom fetch that routes a `@hey-api` `Request` through Forge's
 * `asUser().requestJira`. The Jira SDK builds an absolute URL against a
 * placeholder base; only the path and query are meaningful to `requestJira`,
 * which resolves the real site origin itself.
 */
const forgeJiraFetch: typeof fetch = async (input, init) => {
    const request = new Request(input, init);
    const url = new URL(request.url);
    const routePath = `${url.pathname}${url.search}`;
    const body =
        request.method === 'GET' || request.method === 'HEAD'
            ? undefined
            : await request.text();

    const response = await api
        .asUser()
        .requestJira(assumeTrustedRoute(routePath), {
            method: request.method,
            headers: { Accept: 'application/json' },
            body,
        });

    return response as unknown as Response;
};

/** Shared per-call options binding every Jira operation to the Forge fetch. */
const jiraCallOptions = {
    baseUrl: 'https://jira.invalid',
    fetch: forgeJiraFetch,
} satisfies Partial<Options>;

/**
 * Search Jira site users as the invoking user. An empty query seeds the list
 * via `getAllUsers` (`GET /users/search`); a non-empty query uses `findUsers`
 * (`GET /user/search`), which rejects an empty `query`.
 */
export async function searchUsers(query?: string): Promise<WorkspaceUser[]> {
    const term = query?.trim();

    const result = term
        ? await findUsers({
              ...jiraCallOptions,
              query: { query: term, maxResults: USER_SEARCH_MAX_RESULTS },
          })
        : await getAllUsers({
              ...jiraCallOptions,
              query: { maxResults: USER_SEARCH_MAX_RESULTS },
          });

    if (result.error) {
        throw new Error(
            `Jira user search failed${
                result.response ? ` (status ${result.response.status})` : ''
            }.`,
        );
    }

    return toWorkspaceUsers((result.data ?? []) as User[]);
}
