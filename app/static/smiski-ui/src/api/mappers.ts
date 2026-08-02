import type {
    Meeting,
    MeetingSettings,
    MeetingStatus,
    Participant,
    ParticipantRole,
} from '../domain';

const DEVICE_STORAGE_KEY = 'smiski:device-id';

interface BackendIssueLink {
    issueId?: string;
    issueKey?: string;
    projectKey?: string;
}

interface BackendMeetingSnapshot {
    id?: string;
    title?: string;
    description?: string | null;
    projectId?: string;
    projectKey?: string;
    issueId?: string;
    issueKey?: string;
    issueSummary?: string;
    creatorId?: string;
    creatorName?: string;
    hostId?: string;
    hostName?: string;
    organizerDisplayName?: string;
    type?: string;
    startTime?: string | null;
    startedAt?: string | null;
    endTime?: string | null;
    endedAt?: string | null;
    createdAt?: string | null;
    status?: string;
    issueLink?: BackendIssueLink;
    settings?: MeetingSettings;
    zoneId?: string;
    participantCount?: number;
    inviteeCount?: number;
}

interface BackendParticipantSnapshot {
    accountId?: string;
    displayName?: string;
    role?: string;
    joinedAt?: string | null;
    leftAt?: string | null;
}

type BackendEnvelope = Record<string, unknown>;

/** Overrides for fields the backend doesn't carry on every response shape. */
export interface MeetingFromBackendOverrides {
    /**
     * The backend never returns a participant count on any meeting snapshot
     * (list summary or detail) — only `get`'s sibling `participants` array
     * carries real data. Callers with that array should pass its length here
     * rather than let the mapper's placeholder default stand.
     */
    participantCount?: number;
}

export function meetingFromBackend(
    payload: unknown,
    overrides?: MeetingFromBackendOverrides,
): Meeting {
    const snapshot = extractMeetingSnapshot(payload);
    const issueLink = snapshot.issueLink ?? {};
    const issueKey = issueLink.issueKey ?? snapshot.issueKey ?? '';
    const projectKey =
        issueLink.projectKey
        ?? snapshot.projectKey
        ?? issueKey.split('-')[0]
        ?? '';
    const status = normalizeMeetingStatus(snapshot.status);
    const isInstant = snapshot.type === 'INSTANT';
    const hostId = snapshot.hostId ?? snapshot.creatorId ?? '';
    const hostName =
        snapshot.hostName
        ?? snapshot.organizerDisplayName
        ?? snapshot.creatorName
        ?? 'Unknown host';
    const startTime = snapshot.startTime ?? snapshot.startedAt ?? undefined;
    const createdAt = snapshot.createdAt ?? undefined;
    // A SCHEDULED-type meeting keeps its originally-scheduled time regardless
    // of current status (still worth showing in a completed/canceled meeting's
    // history); startedAt is only meaningful once the meeting has actually run.
    const scheduledAt = !isInstant ? startTime : undefined;
    const startedAt =
        status === 'RUNNING' || status === 'COMPLETED'
            ? ((isInstant ? createdAt : startTime) ?? createdAt)
            : undefined;

    return {
        id: snapshot.id ?? '',
        title: snapshot.title ?? 'Untitled meeting',
        description: snapshot.description ?? undefined,
        projectId: snapshot.projectId ?? `project-${projectKey.toLowerCase()}`,
        projectKey,
        issueId: issueLink.issueId ?? snapshot.issueId ?? `issue-${issueKey}`,
        issueKey,
        issueSummary: snapshot.issueSummary,
        creatorId: snapshot.creatorId ?? hostId,
        creatorName: snapshot.creatorName ?? hostName,
        hostId,
        hostName,
        scheduledAt,
        startedAt,
        endedAt: snapshot.endedAt ?? undefined,
        status,
        participantCount:
            overrides?.participantCount
            ?? snapshot.participantCount
            ?? snapshot.inviteeCount
            ?? 1,
        endTime: snapshot.endTime ?? undefined,
        zoneId: snapshot.zoneId ?? undefined,
        settings: snapshot.settings ?? undefined,
    };
}

/** Maps a `list` response's meeting-summary array to domain `Meeting[]`. */
export function meetingsFromBackend(payload: unknown): Meeting[] {
    return extractArray<BackendMeetingSnapshot>(payload, [
        'data',
        'meetings',
        'items',
        'content',
    ]).map((snapshot) => meetingFromBackend(snapshot));
}

export function participantsFromBackend(payload: unknown): Participant[] {
    return extractArray<BackendParticipantSnapshot>(payload, [
        'participants',
        'items',
        'content',
        'invitees',
        'data',
    ]).map((participant) => ({
        accountId: participant.accountId ?? '',
        displayName:
            participant.displayName
            ?? participant.accountId
            ?? 'Unknown participant',
        role: normalizeParticipantRole(participant.role),
        joinedAt: participant.joinedAt ?? undefined,
        leftAt: participant.leftAt ?? undefined,
    }));
}

export function permissionsFromBackend(payload: unknown): {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
} {
    if (!isEnvelope(payload))
        return { hasViewMeeting: false, hasEditMeeting: false };
    return {
        hasViewMeeting: Boolean(
            payload.hasViewMeeting
                ?? payload.canViewMeeting
                ?? payload.viewMeeting,
        ),
        hasEditMeeting: Boolean(
            payload.hasEditMeeting
                ?? payload.canEditMeeting
                ?? payload.editMeeting,
        ),
    };
}

function extractMeetingSnapshot(payload: unknown): BackendMeetingSnapshot {
    if (isEnvelope(payload) && isEnvelope(payload.meeting)) {
        return payload.meeting as BackendMeetingSnapshot;
    }
    if (isEnvelope(payload)) return payload as BackendMeetingSnapshot;
    return {};
}

function extractArray<T>(payload: unknown, keys: string[]): T[] {
    if (Array.isArray(payload)) return payload as T[];
    if (!isEnvelope(payload)) return [];
    for (const key of keys) {
        const value = payload[key];
        if (Array.isArray(value)) return value as T[];
    }
    return [];
}

/**
 * Stable per-browser device id for the host participant. Persisted in
 * localStorage; falls back to a fixed id when storage is unavailable.
 */
export function getDeviceId(): string {
    try {
        const existing = localStorage.getItem(DEVICE_STORAGE_KEY);
        if (existing) return existing;
        const value = `web-${crypto.randomUUID()}`;
        localStorage.setItem(DEVICE_STORAGE_KEY, value);
        return value;
    } catch {
        return 'web-forge-client';
    }
}

function normalizeMeetingStatus(value: string | undefined): MeetingStatus {
    if (value === 'RUNNING' || value === 'COMPLETED' || value === 'CANCELED')
        return value;
    if (value === 'CANCELLED') return 'CANCELED';
    return 'SCHEDULED';
}

function normalizeParticipantRole(value: string | undefined): ParticipantRole {
    return value === 'HOST' ? 'HOST' : 'PARTICIPANT';
}

function isEnvelope(value: unknown): value is BackendEnvelope {
    return Boolean(value && typeof value === 'object');
}
