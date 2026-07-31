/**
 * Demo meeting store — persists meetings/participants in Forge KVS instead
 * of calling the `meet` backend (Postgres). One KVS document per meeting,
 * keyed `meeting:<id>`; lists page through every stored meeting via
 * `kvs.query()` and filter in memory, a straight port of the filter logic
 * that used to live in the Custom UI's `mocks/db.ts`. Fine at demo scale —
 * no secondary indexes to keep consistent.
 *
 * Callers pass a trusted `MeetingActor` resolved from Jira (never from the
 * client) for every mutation — see `jiraSdkClient.ts`'s `getCurrentJiraUser`.
 */
import { randomUUID } from 'node:crypto';
import { kvs, WhereConditions } from '@forge/kvs';
import type { Meeting, MeetingSettings, Participant } from './domain/meeting';
import { mintLiveKitToken } from './liveKitToken';

const MEETING_KEY_PREFIX = 'meeting:';
// Forge KVS rejects a list query with `limit >= 100`
// (`LIST_QUERY_LIMIT_EXCEEDED`), so this must stay under it. `fetchAllMeetings`
// pages with a cursor, so the value only affects round-trips, not results.
const QUERY_PAGE_SIZE = 90;

type StoredMeeting = Meeting & { participants: Participant[] };

export interface MeetingActor {
    accountId: string;
    displayName: string;
    avatarUrl?: string;
}

export interface IssueLinkInput {
    issueId?: string;
    issueKey: string;
    projectKey?: string;
}

export interface MeetingInviteeInput {
    accountId: string;
    displayName: string;
}

export interface CreateInstantMeetingInput {
    title: string;
    description?: string;
    issueLink: IssueLinkInput;
    settings?: MeetingSettings;
    zoneId?: string;
    invitees?: MeetingInviteeInput[];
}

export interface ScheduleMeetingInput {
    title: string;
    description?: string;
    issueLink: IssueLinkInput;
    settings?: MeetingSettings;
    zoneId?: string;
    timeRange: { startTime: string; endTime: string };
    invitees: MeetingInviteeInput[];
}

export interface UpdateMeetingInput {
    title: string;
    description?: string;
    issueLink: IssueLinkInput;
    settings?: MeetingSettings;
    zoneId?: string;
    timeRange?: { startTime: string; endTime: string };
}

export interface ProjectMeetingFilters {
    projectKey: string;
    issueKey?: string;
    createdByAccountId?: string;
    status?: Meeting['status'];
    search?: string;
}

export interface MeetingDetail {
    meeting: Meeting;
    participants: Participant[];
}

export interface JoinMeetingResult {
    requestId: string;
    token: string;
    roomName: string;
}

function meetingKey(id: string): string {
    return `${MEETING_KEY_PREFIX}${id}`;
}

function generateMeetingId(): string {
    return `m-${randomUUID()}`;
}

function toPublicMeeting(stored: StoredMeeting): Meeting {
    const { participants: _participants, ...meeting } = stored;
    return meeting;
}

async function saveMeeting(stored: StoredMeeting): Promise<void> {
    await kvs.set(meetingKey(stored.id), stored);
}

/**
 * Fills in fields a stored document may be missing, so every read path can
 * treat them as present.
 *
 * KVS documents are schemaless and long-lived: a meeting written by an earlier
 * revision of this file keeps whatever shape it had then, and this code still
 * has to read it. Without this, `joinMeeting`/`endMeeting` crash with
 * "Cannot read properties of undefined (reading 'find')" on any meeting saved
 * before `participants` existed, and the `listProjectMeetings` filters throw on
 * a missing `issueKey`/`title`. Normalizing once on read keeps that concern out
 * of every caller.
 */
function normalizeStoredMeeting(stored: StoredMeeting): StoredMeeting {
    const participants = Array.isArray(stored.participants)
        ? stored.participants
        : [];
    return {
        ...stored,
        participants,
        title: stored.title ?? '',
        issueKey: stored.issueKey ?? '',
        participantCount: stored.participantCount ?? participants.length,
    };
}

async function fetchAllMeetings(): Promise<StoredMeeting[]> {
    const meetings: StoredMeeting[] = [];
    let cursor: string | undefined;
    do {
        let query = kvs
            .query()
            .where('key', WhereConditions.beginsWith(MEETING_KEY_PREFIX))
            .limit(QUERY_PAGE_SIZE);
        if (cursor) query = query.cursor(cursor);
        const page = await query.getMany<StoredMeeting>();
        meetings.push(
            ...page.results.map((result) =>
                normalizeStoredMeeting(result.value),
            ),
        );
        cursor = page.nextCursor;
    } while (cursor);
    return meetings;
}

async function getStoredMeeting(meetingId: string): Promise<StoredMeeting> {
    const stored = await kvs.get<StoredMeeting>(meetingKey(meetingId));
    if (!stored) throw new Error(`Meeting not found: ${meetingId}`);
    return normalizeStoredMeeting(stored);
}

export async function listIssueMeetings(issueKey: string): Promise<Meeting[]> {
    const meetings = await fetchAllMeetings();
    return meetings
        .filter((m) => m.issueKey === issueKey)
        .map(toPublicMeeting);
}

/** Mirrors the old mock's `listProjectMeetings` filter logic exactly. */
export async function listProjectMeetings(
    filters: ProjectMeetingFilters,
): Promise<Meeting[]> {
    const search = filters.search?.trim().toLowerCase();
    const issueKey = filters.issueKey?.trim().toUpperCase();
    const meetings = await fetchAllMeetings();
    return meetings
        .filter((m) => {
            if (m.projectKey !== filters.projectKey) return false;
            if (filters.status && m.status !== filters.status) return false;
            if (
                filters.createdByAccountId
                && m.creatorId !== filters.createdByAccountId
            )
                return false;
            if (issueKey && m.issueKey.toUpperCase() !== issueKey)
                return false;
            if (search && !m.title.toLowerCase().includes(search))
                return false;
            return true;
        })
        .map(toPublicMeeting);
}

export async function getMeeting(meetingId: string): Promise<MeetingDetail> {
    const stored = await getStoredMeeting(meetingId);
    return { meeting: toPublicMeeting(stored), participants: stored.participants };
}

/** Any RUNNING meeting hosted by the actor, optionally excluding one issue. */
export async function findRunningMeetingHostedByUser(
    accountId: string,
    excludingIssueKey?: string,
): Promise<Meeting | null> {
    const meetings = await fetchAllMeetings();
    const hit = meetings.find(
        (m) =>
            m.status === 'RUNNING'
            && m.hostId === accountId
            && m.issueKey !== excludingIssueKey,
    );
    return hit ? toPublicMeeting(hit) : null;
}

function projectKeyOf(issueLink: IssueLinkInput): string {
    return issueLink.projectKey ?? issueLink.issueKey.split('-')[0];
}

export async function createInstantMeeting(
    input: CreateInstantMeetingInput,
    actor: MeetingActor,
): Promise<Meeting> {
    const projectKey = projectKeyOf(input.issueLink);
    const now = new Date().toISOString();
    const participants: Participant[] = [
        {
            accountId: actor.accountId,
            displayName: actor.displayName,
            role: 'HOST',
            joinedAt: now,
        },
        ...(input.invitees ?? []).map(
            (invitee): Participant => ({
                accountId: invitee.accountId,
                displayName: invitee.displayName,
                role: 'PARTICIPANT',
            }),
        ),
    ];

    const meeting: Meeting = {
        id: generateMeetingId(),
        title: input.title,
        description: input.description,
        projectId: `project-${projectKey.toLowerCase()}`,
        projectKey,
        issueId: input.issueLink.issueId ?? `issue-${input.issueLink.issueKey}`,
        issueKey: input.issueLink.issueKey,
        creatorId: actor.accountId,
        creatorName: actor.displayName,
        hostId: actor.accountId,
        hostName: actor.displayName,
        startedAt: now,
        status: 'RUNNING',
        participantCount: participants.length,
        zoneId: input.zoneId,
        settings: input.settings,
    };

    await saveMeeting({ ...meeting, participants });
    return meeting;
}

export async function scheduleMeeting(
    input: ScheduleMeetingInput,
    actor: MeetingActor,
): Promise<Meeting> {
    const projectKey = projectKeyOf(input.issueLink);
    const participants: Participant[] = [
        { accountId: actor.accountId, displayName: actor.displayName, role: 'HOST' },
        ...input.invitees.map(
            (invitee): Participant => ({
                accountId: invitee.accountId,
                displayName: invitee.displayName,
                role: 'PARTICIPANT',
            }),
        ),
    ];

    const meeting: Meeting = {
        id: generateMeetingId(),
        title: input.title,
        description: input.description,
        projectId: `project-${projectKey.toLowerCase()}`,
        projectKey,
        issueId: input.issueLink.issueId ?? `issue-${input.issueLink.issueKey}`,
        issueKey: input.issueLink.issueKey,
        creatorId: actor.accountId,
        creatorName: actor.displayName,
        hostId: actor.accountId,
        hostName: actor.displayName,
        scheduledAt: input.timeRange.startTime,
        endTime: input.timeRange.endTime,
        status: 'SCHEDULED',
        participantCount: participants.length,
        zoneId: input.zoneId,
        settings: input.settings,
    };

    await saveMeeting({ ...meeting, participants });
    return meeting;
}

export async function updateMeeting(
    meetingId: string,
    input: UpdateMeetingInput,
): Promise<Meeting> {
    const stored = await getStoredMeeting(meetingId);
    const updated: StoredMeeting = {
        ...stored,
        title: input.title,
        description: input.description,
        settings: input.settings ?? stored.settings,
        zoneId: input.zoneId ?? stored.zoneId,
        scheduledAt: input.timeRange?.startTime ?? stored.scheduledAt,
        endTime: input.timeRange?.endTime ?? stored.endTime,
    };
    await saveMeeting(updated);
    return toPublicMeeting(updated);
}

/** Cancels (soft-deletes) a meeting. Mirrors the real backend: rejects a RUNNING meeting. */
export async function cancelMeeting(meetingId: string): Promise<Meeting> {
    const stored = await getStoredMeeting(meetingId);
    if (stored.status === 'RUNNING') {
        throw new Error('Cannot cancel a meeting that is currently running.');
    }
    const updated: StoredMeeting = { ...stored, status: 'CANCELED' };
    await saveMeeting(updated);
    return toPublicMeeting(updated);
}

/**
 * Ends a RUNNING meeting. No equivalent exists in the real `meet` backend
 * (there, RUNNING→COMPLETED only happens via an async LiveKit webhook) — this
 * demo implements it directly since there is no backend to defer to.
 */
export async function endMeeting(meetingId: string): Promise<Meeting> {
    const stored = await getStoredMeeting(meetingId);
    const now = new Date().toISOString();
    const updated: StoredMeeting = {
        ...stored,
        status: 'COMPLETED',
        endedAt: now,
        participants: stored.participants.map((p) =>
            p.leftAt ? p : { ...p, leftAt: now },
        ),
    };
    await saveMeeting(updated);
    return toPublicMeeting(updated);
}

/**
 * Joins a meeting, mints its LiveKit token, and (mirroring the real backend's
 * `join` contract) transitions a SCHEDULED meeting to RUNNING when its host
 * joins — there is no separate "start" operation.
 */
export async function joinMeeting(
    meetingId: string,
    actor: MeetingActor,
): Promise<JoinMeetingResult> {
    const stored = await getStoredMeeting(meetingId);
    if (stored.status === 'COMPLETED' || stored.status === 'CANCELED') {
        throw new Error(
            `Cannot join a meeting that is ${stored.status.toLowerCase()}.`,
        );
    }

    const now = new Date().toISOString();
    const isHost = stored.hostId === actor.accountId;
    const isStarting = stored.status === 'SCHEDULED';
    const existing = stored.participants.find(
        (p) => p.accountId === actor.accountId,
    );
    const participants = existing
        ? stored.participants.map((p) =>
              p.accountId === actor.accountId
                  ? { ...p, joinedAt: now, leftAt: undefined }
                  : p,
          )
        : [
              ...stored.participants,
              {
                  accountId: actor.accountId,
                  displayName: actor.displayName,
                  role: isHost ? ('HOST' as const) : ('PARTICIPANT' as const),
                  joinedAt: now,
              },
          ];

    const updated: StoredMeeting = {
        ...stored,
        status: 'RUNNING',
        startedAt: isStarting ? now : stored.startedAt,
        participantCount: participants.length,
        participants,
    };
    await saveMeeting(updated);

    const { token } = await mintLiveKitToken(
        meetingId,
        actor.accountId,
        actor.displayName,
    );
    return { requestId: randomUUID(), token, roomName: meetingId };
}
