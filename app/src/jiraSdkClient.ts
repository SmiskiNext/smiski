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
    getAllPermissions,
    getAllUsers,
    getMyPermissions,
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

const VIEW_MEETING_PERMISSION_NAME = 'View Meeting';
const EDIT_MEETING_PERMISSION_NAME = 'Edit Meeting';

export interface MeetingPermissionResult {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
}

/**
 * Resolves the real Jira permission-key strings for the app's declared
 * `view-meeting`/`edit-meeting` custom permissions (declared in
 * `manifest.yml` under `jira:projectPermission`). Jira does not necessarily
 * echo back the bare manifest `key` when listing permissions — matching by
 * the `name` we declared is stable regardless of any internal key prefixing.
 */
async function resolveMeetingPermissionKeys(): Promise<{
    viewKey: string;
    editKey: string;
}> {
    const result = await getAllPermissions({ ...jiraCallOptions });
    if (result.error) {
        throw new Error(
            `Jira permission lookup failed while listing permissions${
                result.response ? ` (status ${result.response.status})` : ''
            }.`,
        );
    }

    const entries = Object.entries(result.data?.permissions ?? {});
    const viewKey = entries.find(
        ([, permission]) => permission.name === VIEW_MEETING_PERMISSION_NAME,
    )?.[0];
    const editKey = entries.find(
        ([, permission]) => permission.name === EDIT_MEETING_PERMISSION_NAME,
    )?.[0];

    if (!viewKey || !editKey) {
        throw new Error(
            'View Meeting / Edit Meeting permissions are not registered on this '
                + 'Jira site yet — deploy the app and confirm the jira:projectPermission '
                + 'module is installed.',
        );
    }

    return { viewKey, editKey };
}

/**
 * Checks the invoking user's `View Meeting`/`Edit Meeting` custom permission
 * for a project, via `GET /rest/api/3/mypermissions` as the invoking user.
 */
export async function getMeetingPermission(
    projectKey: string,
): Promise<MeetingPermissionResult> {
    const { viewKey, editKey } = await resolveMeetingPermissionKeys();

    const result = await getMyPermissions({
        ...jiraCallOptions,
        query: { projectKey, permissions: `${viewKey},${editKey}` },
    });
    if (result.error) {
        throw new Error(
            `Jira permission check failed${
                result.response ? ` (status ${result.response.status})` : ''
            }.`,
        );
    }

    const permissions = result.data?.permissions ?? {};
    return {
        hasViewMeeting: Boolean(permissions[viewKey]?.havePermission),
        hasEditMeeting: Boolean(permissions[editKey]?.havePermission),
    };
}
