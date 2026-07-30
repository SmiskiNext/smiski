/**
 * Forge resolver — backend entry point (STUB).
 *
 * Responsibility (future): thin bridge between the Custom UI frontend and the
 * Kong Gateway. Each resolver reads the invoking Jira user's context, forwards
 * the request to the backend (tenant/meet/record services) with the user's
 * identity, and returns the result. It holds NO business logic itself — the
 * "business brain" lives in the backend `meeting-management` service.
 *
 * NOTE: Every definition below is an unimplemented placeholder. No fetch, no
 * auth bridging, no data shaping is wired up yet. Do not add business logic
 * here — see architecture_vi.md (Forge Remote / JWT-JWKS auth bridge).
 */
import Resolver from '@forge/resolver';
import { getMeetingPermission, searchUsers } from './jiraSdkClient';

const resolver = new Resolver();

// TODO(UC02/UC07): list meetings for the current Issue.
resolver.define('getIssueMeetings', async (_req) => {
    throw new Error('Not implemented: getIssueMeetings');
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

// NOTE(UC01): instant meetings are created by the Custom UI calling the `meet`
// backend directly via Forge Remote (`requestRemote`), so Forge attaches the
// signed FIT and the app asserts no tenant/account identity. There is no
// resolver for it here — see static/smiski-ui/src/api/meetings.ts.

// TODO(UC03): create a scheduled meeting bound to the current Issue.
resolver.define('scheduleMeeting', async (_req) => {
    throw new Error('Not implemented: scheduleMeeting');
});

// TODO: list/search meetings across a project (project page dashboard).
resolver.define('getProjectMeetings', async (_req) => {
    throw new Error('Not implemented: getProjectMeetings');
});

/**
 * Resolves the invoking user's `View Meeting`/`Edit Meeting` custom Jira
 * permission (declared in `manifest.yml` under `jira:projectPermission`) for
 * a project, via `asUser().requestJira` — see `jiraSdkClient.ts`.
 *
 * NOTE: this is UI gating only. The `meet` backend does not yet re-check
 * this permission itself (it only checks meeting-host ownership on
 * update/delete) — see app/AGENTS.md for the researched follow-up mechanism
 * (`appUserToken` + a direct Jira REST call from the backend).
 */
resolver.define('getMeetingPermission', async (req) => {
    const projectKey = req.payload?.projectKey as string | undefined;
    if (!projectKey)
        throw new Error('getMeetingPermission: projectKey is required');
    return getMeetingPermission(projectKey);
});

// NOTE: room-token minting no longer lives here. The Custom UI calls the
// real `meet` backend's `join` operation directly via Forge Remote
// (`static/smiski-ui/src/api/meetings.ts`'s `getRoomToken`/`joinMeeting`),
// which authorizes the request against the actual meeting/participant
// roster — replacing the insecure local-JWT-minting shim this resolver used
// to provide.

export const handler = resolver.getDefinitions();
