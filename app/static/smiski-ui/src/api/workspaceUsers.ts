/**
 * Workspace-user search — calls Jira directly from the browser via
 * `@smiskinext/sdks-jira` bridged to `@forge/bridge`'s `requestJira`
 * (`jiraSdkFetch.ts`), so Jira enforces the invoking user's own "Browse
 * users" permission. No resolver hop: Forge bridge v2+ supports Jira REST
 * calls natively from Custom UI, same as `issues.ts`/`projectMembers.ts`.
 */
import { findUsers, getAllUsers, type User } from '@smiskinext/sdks-jira';
import { jiraCallOptions } from './jiraSdkFetch';
import { toWorkspaceUsers } from './workspaceUserMapping';

export type { WorkspaceUser } from './workspaceUserMapping';

const USER_SEARCH_MAX_RESULTS = 50;

/**
 * Search Jira site users as the invoking user. An empty query seeds the list
 * via `getAllUsers` (`GET /users/search`); a non-empty query uses `findUsers`
 * (`GET /user/search`), which rejects an empty `query`.
 */
export async function searchWorkspaceUsers(query?: string) {
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
