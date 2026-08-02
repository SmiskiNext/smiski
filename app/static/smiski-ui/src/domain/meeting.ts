import type { MeetingStatus } from './enums';

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

export interface MeetingPermissions {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
    canViewMeeting: boolean;
    canEditMeeting: boolean;
    isLoading: boolean;
    error: Error | null;
}
