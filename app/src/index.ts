/**
 * Forge resolver — backend entry point.
 *
 * Demo branch: meeting reads/mutations are served from Forge KVS
 * (`meetingStore.ts`) instead of the real `meet` backend, so this resolver is
 * no longer a thin pass-through for those — it is the permission-enforcement
 * + identity-resolution layer the real backend doesn't have yet (see
 * app/AGENTS.md's "Backend permission enforcement (not yet built)").
 * `searchWorkspaceUsers`/`getMeetingPermission` are unchanged from before.
 */
import Resolver from '@forge/resolver';
import {
    getCurrentJiraUser,
    getMeetingPermission,
    searchUsers,
} from './jiraSdkClient';
import * as meetingStore from './meetingStore';

const resolver = new Resolver();

type PermissionLevel = 'VIEW' | 'EDIT';

/**
 * Enforces the invoking user's custom `View Meeting`/`Edit Meeting` Jira
 * project permission before a KVS read/write proceeds. `EDIT` implies `VIEW`
 * at the policy layer (mirrors `domain/meetingPolicy.ts`'s
 * `resolveMeetingPermissions` on the frontend) — the real authorization
 * boundary the UI-only check in the pre-KVS version of this resolver lacked.
 */
async function requireMeetingPermission(
    projectKey: string,
    level: PermissionLevel,
): Promise<void> {
    const permission = await getMeetingPermission(projectKey);
    const allowed =
        level === 'EDIT'
            ? permission.hasEditMeeting
            : permission.hasViewMeeting || permission.hasEditMeeting;
    if (!allowed) {
        const label = level === 'EDIT' ? 'Edit Meeting' : 'View Meeting';
        throw new Error(
            `${label} permission required for project ${projectKey}.`,
        );
    }
}

function projectKeyOfIssue(issueKey: string): string {
    return issueKey.split('-')[0];
}

resolver.define('getIssueMeetings', async (req) => {
    const issueKey = req.payload?.issueKey as string | undefined;
    if (!issueKey) throw new Error('getIssueMeetings: issueKey is required');
    await requireMeetingPermission(projectKeyOfIssue(issueKey), 'VIEW');
    return meetingStore.listIssueMeetings(issueKey);
});

resolver.define('getProjectMeetings', async (req) => {
    const filters = req.payload as
        | meetingStore.ProjectMeetingFilters
        | undefined;
    if (!filters?.projectKey)
        throw new Error('getProjectMeetings: projectKey is required');
    await requireMeetingPermission(filters.projectKey, 'VIEW');
    return meetingStore.listProjectMeetings(filters);
});

resolver.define('getMeeting', async (req) => {
    const meetingId = req.payload?.meetingId as string | undefined;
    if (!meetingId) throw new Error('getMeeting: meetingId is required');
    const detail = await meetingStore.getMeeting(meetingId);
    await requireMeetingPermission(detail.meeting.projectKey, 'VIEW');
    return detail;
});

resolver.define('createInstantMeeting', async (req) => {
    const input = req.payload as
        | meetingStore.CreateInstantMeetingInput
        | undefined;
    if (!input?.issueLink?.issueKey)
        throw new Error('createInstantMeeting: issueLink.issueKey is required');
    await requireMeetingPermission(
        input.issueLink.projectKey ?? projectKeyOfIssue(input.issueLink.issueKey),
        'EDIT',
    );
    const actor = await getCurrentJiraUser();
    return meetingStore.createInstantMeeting(input, actor);
});

resolver.define('scheduleMeeting', async (req) => {
    const input = req.payload as meetingStore.ScheduleMeetingInput | undefined;
    if (!input?.issueLink?.issueKey)
        throw new Error('scheduleMeeting: issueLink.issueKey is required');
    await requireMeetingPermission(
        input.issueLink.projectKey ?? projectKeyOfIssue(input.issueLink.issueKey),
        'EDIT',
    );
    const actor = await getCurrentJiraUser();
    return meetingStore.scheduleMeeting(input, actor);
});

resolver.define('updateMeeting', async (req) => {
    const meetingId = req.payload?.meetingId as string | undefined;
    const input = req.payload?.input as
        | meetingStore.UpdateMeetingInput
        | undefined;
    if (!meetingId || !input)
        throw new Error('updateMeeting: meetingId and input are required');
    const existing = await meetingStore.getMeeting(meetingId);
    await requireMeetingPermission(existing.meeting.projectKey, 'EDIT');
    return meetingStore.updateMeeting(meetingId, input);
});

resolver.define('cancelMeeting', async (req) => {
    const meetingId = req.payload?.meetingId as string | undefined;
    if (!meetingId) throw new Error('cancelMeeting: meetingId is required');
    const existing = await meetingStore.getMeeting(meetingId);
    await requireMeetingPermission(existing.meeting.projectKey, 'EDIT');
    return meetingStore.cancelMeeting(meetingId);
});

// No separate "start" operation — joining a SCHEDULED meeting (below)
// transitions it to RUNNING, mirroring the real backend's `join` contract.

resolver.define('endMeeting', async (req) => {
    const meetingId = req.payload?.meetingId as string | undefined;
    if (!meetingId) throw new Error('endMeeting: meetingId is required');
    const existing = await meetingStore.getMeeting(meetingId);
    await requireMeetingPermission(existing.meeting.projectKey, 'EDIT');
    return meetingStore.endMeeting(meetingId);
});

/**
 * Joins a meeting and mints its LiveKit token. Identity is resolved from
 * Jira (`getCurrentJiraUser`), never trusted from the client payload — the
 * Custom UI's `displayName`/`avatarUrl` arguments to `joinMeeting`/
 * `getRoomToken` are for the old backend's contract shape and are ignored
 * here.
 */
resolver.define('joinMeeting', async (req) => {
    const meetingId = req.payload?.meetingId as string | undefined;
    if (!meetingId) throw new Error('joinMeeting: meetingId is required');
    const existing = await meetingStore.getMeeting(meetingId);
    await requireMeetingPermission(existing.meeting.projectKey, 'VIEW');
    const actor = await getCurrentJiraUser();
    return meetingStore.joinMeeting(meetingId, actor);
});

/**
 * Is the invoking user already hosting a RUNNING meeting on a different
 * issue? Backs the Issue Panel's "confirm before starting a second
 * concurrent meeting" prompt (UC-01 alt flow).
 */
resolver.define('getHostConflict', async (req) => {
    const excludingIssueKey = req.payload?.excludingIssueKey as
        | string
        | undefined;
    const actor = await getCurrentJiraUser();
    return meetingStore.findRunningMeetingHostedByUser(
        actor.accountId,
        excludingIssueKey,
    );
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
