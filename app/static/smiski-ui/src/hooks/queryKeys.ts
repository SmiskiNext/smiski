/**
 * Centralized React Query key factories — keeps cache invalidation in
 * mutation hooks in sync with the query hooks that read the same data.
 */
import type { ProjectMeetingListParams } from '../api/meetings';
import type { PendingJoinRequestsPageParams } from '../domain';
import { BUILD_VERSION } from '../utils/buildVersion';

const CURRENT_USER_KEY = ['jira', 'current-user'] as const;
const MEETING_PERMISSION_KEYS_ROOT = 'meeting-permission-keys';
const MEETING_PERMISSIONS_ROOT = 'meeting-permissions';

export const queryKeys = {
    currentUser: CURRENT_USER_KEY,
    /**
     * Jira's real permission-key strings for the app's declared custom
     * permissions. Scoped by build version rather than by user or project: the
     * keys are a property of the deployed `manifest.yml`, identical for every
     * user and every project on the site, so one resolution serves them all
     * and only a redeploy can invalidate it.
     */
    meetingPermissionKeys: () =>
        [MEETING_PERMISSION_KEYS_ROOT, BUILD_VERSION] as const,
    /**
     * One user's custom meeting permissions in one project. Scoped per user and
     * per project because Jira answers `mypermissions` for the invoking user
     * against a single project.
     */
    meetingPermissions: (projectKey: string, accountId: string) =>
        [MEETING_PERMISSIONS_ROOT, projectKey, accountId] as const,
    issueMeetings: (issueId: string) => ['meetings', 'issue', issueId] as const,
    projectMeetings: (filters: Partial<ProjectMeetingListParams>) =>
        ['meetings', 'project', filters] as const,
    projectIssues: (projectKey: string, query?: string) =>
        ['issues', 'project', projectKey, query ?? ''] as const,
    projectMembers: (projectKey: string) =>
        ['members', 'project', projectKey] as const,
    workspaceUsers: (query: string) => ['workspace-users', query] as const,
    meeting: (meetingId: string) => ['meeting', meetingId] as const,
    pendingJoinRequests: (meetingId: string) =>
        ['meeting', meetingId, 'join-requests', 'pending'] as const,
    pendingJoinRequestsPage: (
        meetingId: string,
        params: PendingJoinRequestsPageParams,
    ) => [...queryKeys.pendingJoinRequests(meetingId), params] as const,
    hostConflict: (accountId: string, excludingIssueKey?: string) =>
        ['host-conflict', accountId, excludingIssueKey] as const,
    roomToken: (meetingId: string) => ['room-token', meetingId] as const,
};

/**
 * The only cache keys `hooks/queryPersistence.ts` may write to `localStorage`.
 *
 * An allowlist rather than a denylist, because the default for anything this
 * app caches must be "not persisted": meetings, issues, members, workspace
 * users, join requests and room tokens all describe live state, and restoring
 * any of them from a previous session would show the user something that is no
 * longer true. Only these three are safe, and each for its own reason — the
 * permission-key resolution changes only on redeploy (and is already scoped by
 * build version), a permission check changes only when an admin edits the
 * project's permission scheme, and the invoking user's identity is fixed for
 * the session.
 *
 * Each entry is a key prefix matched positionally, so the per-project and
 * per-user permission checks are covered without enumerating them. The roots
 * come from the same constants the factories above use, so a renamed key
 * cannot silently fall out of the allowlist.
 */
export const PERSISTED_QUERY_KEY_PREFIXES: ReadonlyArray<
    ReadonlyArray<unknown>
> = [
    CURRENT_USER_KEY,
    [MEETING_PERMISSION_KEYS_ROOT],
    [MEETING_PERMISSIONS_ROOT],
];

/** Whether a cache key is one of the {@link PERSISTED_QUERY_KEY_PREFIXES}. */
export function isPersistedQueryKey(queryKey: ReadonlyArray<unknown>): boolean {
    return PERSISTED_QUERY_KEY_PREFIXES.some(
        (prefix) =>
            queryKey.length >= prefix.length
            && prefix.every((segment, index) => queryKey[index] === segment),
    );
}
