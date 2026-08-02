import type { MeetingStatus } from './enums';
import type { ProjectMember } from './projectMember';

/**
 * Meeting settings as returned by the backend's `get`/`update` detail
 * responses. Only present on `Meeting` objects sourced from those endpoints —
 * list-summary responses don't include it.
 */
export interface MeetingSettings {
    /** Backend types this as a plain string, not a closed enum. */
    admissionPolicy: string;
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
}

/** Canonical meeting model shared by dashboard policy, API clients, and UI. */
export interface Meeting {
    id: string;
    title: string;
    description?: string;
    projectId: string;
    projectKey: string;
    issueId: string;
    issueKey: string;
    issueSummary?: string;
    creatorId: string;
    creatorName: string;
    hostId: string;
    hostName: string;
    scheduledAt?: string;
    startedAt?: string;
    endedAt?: string;
    status: MeetingStatus;
    participantCount: number;
    /** Scheduled end time. Only present when sourced from a detail response. */
    endTime?: string;
    /** IANA zone id. Only present when sourced from a detail response. */
    zoneId?: string;
    /** Only present when sourced from a detail (`get`/`update`) response. */
    settings?: MeetingSettings;
}

/**
 * Resolve `hostName`/`creatorName` against the latest Jira directory (current
 * user + project members), by `hostId`/`creatorId`. The backend's `list`
 * summary shape carries no host display name at all (only `hostId`), so
 * `meetingFromBackend` falls back to a placeholder for list-sourced meetings
 * — this fills it in the same way `resolveParticipantDisplayNames` already
 * does for participants, and also refreshes a stale name on detail-sourced
 * meetings if the directory has since changed.
 */
export function resolveMeetingHostNames(
    meetings: Meeting[],
    directory: ProjectMember[],
): Meeting[] {
    const memberByAccountId = new Map(
        directory.map((member) => [member.accountId, member] as const),
    );

    return meetings.map((meeting) => {
        const host = memberByAccountId.get(meeting.hostId);
        const creator = memberByAccountId.get(meeting.creatorId);
        if (
            (!host || host.displayName === meeting.hostName)
            && (!creator || creator.displayName === meeting.creatorName)
        ) {
            return meeting;
        }
        return {
            ...meeting,
            hostName: host?.displayName ?? meeting.hostName,
            creatorName: creator?.displayName ?? meeting.creatorName,
        };
    });
}

export interface MeetingPermissions {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
    canViewMeeting: boolean;
    canEditMeeting: boolean;
    isLoading: boolean;
    error: Error | null;
}
