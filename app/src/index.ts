/**
 * Forge resolver — backend entry point.
 *
 * Thin bridge, not a brain: meeting reads/mutations go straight from the
 * Custom UI to the real `meet` backend via Forge Remote + the generated SDK
 * (see `static/smiski-ui/src/api/meetings.ts`), so this resolver holds no
 * meeting business logic. `searchWorkspaceUsers` and `getMeetingPermission`
 * are the two operations that must run resolver-side (they call Jira REST as
 * the invoking user). `getProjectMeetings` stays an unimplemented stub — the
 * real `meet` backend has no project-wide listing filter yet.
 */
import Resolver from '@forge/resolver';
import { getMeetingPermission, searchUsers } from './jiraSdkClient';

const resolver = new Resolver();

// TODO: list/search meetings across a project (project page dashboard) —
// the backend `list` operation has no projectKey filter yet.
resolver.define('getProjectMeetings', async (_req) => {
    throw new Error('Not implemented: getProjectMeetings');
});

/**
 * Search Jira site (workspace) users as the invoking user. Identity is derived
 * from the Forge context, never from the browser, so Jira enforces the user's
 * own "Browse users" permission and a rejection (e.g. 403) surfaces as an error
 * rather than a user list.
 */
resolver.define('searchWorkspaceUsers', async (req) => {
    const query = (req.payload?.query as string | undefined) ?? '';
    return searchUsers(query);
});

/**
 * Resolves the invoking user's `View Meeting`/`Edit Meeting` custom Jira
 * permission (declared in `manifest.yml` under `jira:projectPermission`) for
 * a project, via `asUser().requestJira` — see `jiraSdkClient.ts`.
 */
resolver.define('getMeetingPermission', async (req) => {
    const projectKey = req.payload?.projectKey as string | undefined;
    if (!projectKey)
        throw new Error('getMeetingPermission: projectKey is required');
    return getMeetingPermission(projectKey);
});

export const handler = resolver.getDefinitions();
