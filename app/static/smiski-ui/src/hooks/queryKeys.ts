/**
 * Centralized React Query key factories — keeps cache invalidation in
 * mutation hooks in sync with the query hooks that read the same data.
 */
import type { MeetingListFilters } from '../api/meetings';
import type { PendingJoinRequestsPageParams } from '../domain';

export const queryKeys = {
    currentUser: ['jira', 'current-user'] as const,
    issueMeetings: (issueKey: string) =>
        ['meetings', 'issue', issueKey] as const,
    projectMeetings: (filters: Partial<MeetingListFilters>) =>
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
