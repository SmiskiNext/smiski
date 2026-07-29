import type {
    Meeting,
    MeetingStatus,
    Participant,
    ParticipantRole,
    Recording,
    RecordingStatus,
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
    startTime?: string | null;
    startedAt?: string | null;
    endTime?: string | null;
    endedAt?: string | null;
    createdAt?: string | null;
    status?: string;
    issueLink?: BackendIssueLink;
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

interface BackendRecordingSnapshot {
    id?: string;
    meetingId?: string;
    status?: string;
    fileUrl?: string | null;
    playbackUrl?: string | null;
    thumbnailUrl?: string | null;
    title?: string | null;
    notes?: string | null;
    durationSeconds?: number | null;
    startedAt?: string | null;
    endedAt?: string | null;
}

type BackendEnvelope = Record<string, unknown>;

export function meetingFromBackend(payload: unknown): Meeting {
    const snapshot = extractMeetingSnapshot(payload);
    const issueLink = snapshot.issueLink ?? {};
    const issueKey = issueLink.issueKey ?? snapshot.issueKey ?? '';
    const projectKey =
        issueLink.projectKey
        ?? snapshot.projectKey
        ?? issueKey.split('-')[0]
        ?? '';
    const status = normalizeMeetingStatus(snapshot.status);
    const hostId = snapshot.hostId ?? snapshot.creatorId ?? '';
    const hostName =
        snapshot.hostName
        ?? snapshot.organizerDisplayName
        ?? snapshot.creatorName
        ?? 'Unknown host';
    const startTime = snapshot.startTime ?? snapshot.startedAt ?? undefined;
    const createdAt = snapshot.createdAt ?? undefined;

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
        scheduledAt: status === 'SCHEDULED' ? startTime : undefined,
        startedAt: status === 'RUNNING' ? (startTime ?? createdAt) : undefined,
        endedAt: snapshot.endedAt ?? snapshot.endTime ?? undefined,
        status,
        participantCount:
            snapshot.participantCount ?? snapshot.inviteeCount ?? 1,
    };
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

export function recordingFromBackend(payload: unknown): Recording | null {
    if (payload == null) return null;
    const snapshot =
        isEnvelope(payload) && payload.recording ? payload.recording : payload;
    if (!isEnvelope(snapshot)) return null;
    const recording = snapshot as BackendRecordingSnapshot;
    if (!recording.id && !recording.meetingId) return null;
    return {
        id: recording.id ?? '',
        meetingId: recording.meetingId ?? '',
        status: normalizeRecordingStatus(recording.status),
        fileUrl: recording.fileUrl ?? recording.playbackUrl ?? undefined,
        thumbnailUrl: recording.thumbnailUrl ?? undefined,
        title: recording.title ?? undefined,
        notes: recording.notes ?? undefined,
        durationSeconds: recording.durationSeconds ?? undefined,
        startedAt: recording.startedAt ?? undefined,
        endedAt: recording.endedAt ?? undefined,
    };
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

function normalizeRecordingStatus(value: string | undefined): RecordingStatus {
    if (value === 'RECORDING' || value === 'COMPLETED' || value === 'FAILED')
        return value;
    return 'PENDING';
}

function isEnvelope(value: unknown): value is BackendEnvelope {
    return Boolean(value && typeof value === 'object');
}
