import type {
    Meeting,
    MeetingStatus,
    Participant,
    ParticipantRole,
    ProjectMember,
    Recording,
    RecordingStatus,
} from '../domain';
import { getLocalTimeZone } from '../utils/datetime';
import { apiConfig } from './config';
import type { ScheduleMeetingInput, UpdateMeetingInput } from './meetings';

const DEFAULT_DURATION_MINUTES = 60;
const DEVICE_STORAGE_KEY = 'smiski:device-id';

const DEFAULT_MEETING_SETTINGS = {
    admissionPolicy: 'OPEN',
    maxParticipants: 50,
    allowScreenShare: true,
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: true,
};

export interface MeetingApiContext {
    currentUser?: ProjectMember;
    projectMembers?: ProjectMember[];
    projectKey?: string;
    issueId?: string;
}

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

interface BackendLiveKitSnapshot {
    token?: string;
    url?: string;
    livekitUrl?: string;
    roomName?: string;
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

export function meetingsFromBackend(payload: unknown): Meeting[] {
    return extractArray<BackendMeetingSnapshot>(payload, [
        'meetings',
        'items',
        'content',
        'data',
    ]).map(meetingFromBackend);
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

export function roomTokenFromBackend(payload: unknown): {
    token: string;
    url: string;
} {
    const source = liveKitFromBackend(payload);
    const token = source.token;
    const url = source.url ?? source.livekitUrl ?? apiConfig.liveKitUrl;
    if (!token || !url)
        throw new Error('Backend did not return LiveKit token and URL.');
    return { token, url };
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

export function scheduleMeetingRequest(
    input: ScheduleMeetingInput,
    context: MeetingApiContext = {},
): Record<string, unknown> {
    return {
        title: input.title,
        description: nonEmpty(input.description, input.title),
        issueLink: issueLinkFromInput(input, context),
        settings: DEFAULT_MEETING_SETTINGS,
        timeRange: timeRangeFromStart(
            input.startTime,
            input.endTime,
            input.durationMinutes,
        ),
        organizerEmail: emailFor(context.currentUser),
        organizerDisplayName: context.currentUser?.displayName ?? 'Jira user',
        zoneId: input.zoneId ?? getLocalTimeZone(),
        invitees: inviteesFromAccountIds(input.participantAccountIds, context),
    };
}

export function updateMeetingRequest(
    input: UpdateMeetingInput,
    context: MeetingApiContext = {},
): Record<string, unknown> {
    const base = input.baseMeeting;
    const title = nonEmpty(input.title, base?.title ?? 'Untitled meeting');
    const startTime = input.startTime ?? base?.scheduledAt ?? base?.startedAt;
    if (!startTime)
        throw new Error(
            'A start time is required to update a backend meeting.',
        );

    return {
        title,
        description: nonEmpty(input.description, base?.description ?? title),
        issueLink: issueLinkFromMeeting(base, input, context),
        settings: DEFAULT_MEETING_SETTINGS,
        zoneId: input.zoneId ?? getLocalTimeZone(),
        timeRange: timeRangeFromStart(
            startTime,
            input.endTime,
            input.durationMinutes,
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

function liveKitFromBackend(payload: unknown): BackendLiveKitSnapshot {
    if (isEnvelope(payload) && isEnvelope(payload.livekit)) {
        return payload.livekit as BackendLiveKitSnapshot;
    }
    if (isEnvelope(payload)) return payload as BackendLiveKitSnapshot;
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

function issueLinkFromInput(
    input: { issueKey: string; issueId?: string; projectKey?: string },
    context: MeetingApiContext,
): BackendIssueLink {
    const projectKey =
        input.projectKey ?? context.projectKey ?? input.issueKey.split('-')[0];
    return {
        issueId: input.issueId ?? context.issueId ?? `issue-${input.issueKey}`,
        issueKey: input.issueKey,
        projectKey,
    };
}

function issueLinkFromMeeting(
    meeting: Meeting | undefined,
    input: UpdateMeetingInput,
    context: MeetingApiContext,
): BackendIssueLink {
    const issueKey = input.issueKey ?? meeting?.issueKey;
    if (!issueKey)
        throw new Error(
            'An issue key is required to update a backend meeting.',
        );
    const projectKey =
        input.projectKey
        ?? context.projectKey
        ?? meeting?.projectKey
        ?? issueKey.split('-')[0];
    return {
        issueId:
            input.issueId
            ?? context.issueId
            ?? meeting?.issueId
            ?? `issue-${issueKey}`,
        issueKey,
        projectKey,
    };
}

function timeRangeFromStart(
    startTime: string,
    endTime?: string,
    durationMinutes = DEFAULT_DURATION_MINUTES,
): { startTime: string; endTime: string } {
    const start = new Date(startTime);
    const end = endTime
        ? new Date(endTime)
        : new Date(start.getTime() + durationMinutes * 60 * 1000);
    return { startTime: start.toISOString(), endTime: end.toISOString() };
}

function inviteesFromAccountIds(
    accountIds: string[] | undefined,
    context: MeetingApiContext,
): Array<{ accountId: string; displayName: string; email: string }> {
    const members = new Map(
        (context.projectMembers ?? []).map((member) => [
            member.accountId,
            member,
        ]),
    );
    return (accountIds ?? []).map((accountId) => {
        const member = members.get(accountId);
        return {
            accountId,
            displayName: member?.displayName ?? accountId,
            email: emailFor(member ?? { accountId, displayName: accountId }),
        };
    });
}

function emailFor(member: ProjectMember | undefined): string {
    if (member?.email) return member.email;
    const accountId = member?.accountId ?? 'unknown';
    const safe = accountId
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');
    return `smiski+${safe || 'user'}@example.invalid`;
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

function nonEmpty(value: string | undefined, fallback: string): string {
    return value?.trim() || fallback.trim() || 'No description';
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
