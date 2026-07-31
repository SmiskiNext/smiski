import { invoke } from '@forge/bridge';
import type {
    MeetCreateInstantMeetingRequest,
    MeetScheduleMeetingRequest,
    MeetUpdateMeetingRequest,
} from '@smiskinext/smiski-ts';
import type { Meeting, MeetingStatus, Participant } from '../domain';
import { getLocalTimeZone } from '../utils/datetime';
import { apiConfig } from './config';
import { getDeviceId } from './mappers';

/** A meeting invitee, carrying the identity the backend requires. */
export interface MeetingInviteeInput {
    accountId: string;
    displayName: string;
    email: string;
}

/** The invoking user's identity used to populate host/organizer fields. */
export interface InstantMeetingHostIdentity {
    accountId: string;
    displayName: string;
    email?: string;
    avatarUrl?: string;
}

/** Payload to create an instant meeting (UC01). */
export interface CreateInstantMeetingInput {
    issueKey: string;
    issueId?: string;
    projectKey?: string;
    title: string;
    description?: string;
    zoneId?: string;
    /** Full invitees carrying accountId, displayName, and email. */
    invitees?: MeetingInviteeInput[];
    /** Host identity (from CurrentUserContext); resolves host/organizer fields. */
    host?: InstantMeetingHostIdentity;
}

/** Payload to schedule a meeting (UC03). */
export interface ScheduleMeetingInput {
    issueKey: string;
    issueId?: string;
    projectKey?: string;
    title: string;
    /** ISO-8601 UTC instant for the scheduled start. */
    startTime: string;
    /** ISO-8601 UTC instant for the scheduled end. */
    endTime: string;
    zoneId?: string;
    description?: string;
    /** Invitees carrying full identity (accountId, displayName, email). */
    invitees: MeetingInviteeInput[];
    /** Organizer identity (from CurrentUserContext); resolves organizer fields. */
    organizer?: InstantMeetingHostIdentity;
}

/**
 * Edit of an existing meeting. The resolver's `updateMeeting` is a full
 * replace (`title`/`description`/`issueLink`/`settings`/`zoneId` all
 * required), but the edit form only lets a user change title, description,
 * and start time — so `detail` (the meeting's current full detail, from
 * `getMeeting`) supplies everything else unchanged.
 */
export interface UpdateMeetingInput {
    title: string;
    description: string;
    startTime: string;
    detail: Meeting;
}

/** Filters for the project-page dashboard listing. */
export interface MeetingListFilters {
    projectKey: string;
    issueKey?: string;
    createdByAccountId?: string;
    status?: MeetingStatus;
    search?: string;
}

/**
 * A resolver-thrown problem mapped for the create/schedule modals. Carries at
 * least a human-readable `message`; `code`/`traceId`/`status` are only ever
 * populated when a future error-shaping layer adds them — plain `invoke()`
 * rejections only have `message`.
 */
export interface MeetingProblem {
    message: string;
    code?: string;
    traceId?: string;
    status?: number;
}

/** Non-throwing result of instant creation: the meeting, or a problem. */
export interface CreateInstantMeetingResult {
    data?: Meeting;
    error?: MeetingProblem;
}

/** Non-throwing result of scheduled creation: the meeting, or a problem. */
export interface ScheduleMeetingResult {
    data?: Meeting;
    error?: MeetingProblem;
}

/** Default meeting settings shared by the instant and scheduled create flows. */
const DEFAULT_MEETING_SETTINGS = {
    admissionPolicy: 'OPEN',
    maxParticipants: 50,
    allowScreenShare: true,
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: true,
} as const;

/**
 * Build the instant-create request body from the form input. Kept in the
 * `MeetCreateInstantMeetingRequest` shape (nested `issueLink`, a `settings`
 * object, a `host` object, resolved `zoneId`, invitees) even though the demo
 * resolver only reads a subset of it (`host`/`organizerEmail` are ignored —
 * the resolver resolves the actor's identity from Jira itself, see
 * `app/src/index.ts`) — keeping one payload shape means this pure builder,
 * and its unit tests, don't need to change. The `deviceId` is passed in so
 * the browser-only `localStorage` lookup stays out of this pure builder.
 */
export function buildInstantMeetingPayload(
    input: CreateInstantMeetingInput,
    deviceId: string,
): MeetCreateInstantMeetingRequest {
    const projectKey = input.projectKey ?? input.issueKey.split('-')[0];
    return {
        title: input.title,
        description: input.description?.trim() || input.title,
        issueLink: {
            issueId: input.issueId,
            issueKey: input.issueKey,
            projectKey,
        },
        settings: { ...DEFAULT_MEETING_SETTINGS },
        host: {
            displayName: input.host?.displayName ?? 'Jira user',
            deviceId,
            avatarUrl: input.host?.avatarUrl,
        },
        organizerEmail: input.host?.email ?? '',
        organizerDisplayName: input.host?.displayName ?? 'Jira user',
        zoneId: input.zoneId ?? getLocalTimeZone(),
        invitees: (input.invitees ?? []).map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email,
        })),
    };
}

/**
 * Build the scheduled-create request body from the form input. Same
 * `MeetScheduleMeetingRequest` shape as before (`organizerEmail`/
 * `organizerDisplayName` are ignored by the demo resolver, which resolves
 * the actor from Jira) — see `buildInstantMeetingPayload`'s note.
 */
export function buildScheduleMeetingPayload(
    input: ScheduleMeetingInput,
): MeetScheduleMeetingRequest {
    const projectKey = input.projectKey ?? input.issueKey.split('-')[0];
    return {
        title: input.title,
        description: input.description?.trim() || input.title,
        issueLink: {
            issueId: input.issueId,
            issueKey: input.issueKey,
            projectKey,
        },
        settings: { ...DEFAULT_MEETING_SETTINGS },
        timeRange: {
            startTime: input.startTime,
            endTime: input.endTime,
        },
        organizerEmail: input.organizer?.email ?? '',
        organizerDisplayName: input.organizer?.displayName ?? 'Jira user',
        zoneId: input.zoneId ?? getLocalTimeZone(),
        invitees: input.invitees.map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email,
        })),
    };
}

/**
 * Map a resolver rejection — a thrown `Error` (via `@forge/bridge`'s
 * `invoke()`, which prefixes "There was an error invoking the function - ")
 * or a raw string — to the `MeetingProblem` the modals render, always
 * yielding a human-readable `message`.
 */
function toMeetingProblem(source: unknown): MeetingProblem {
    if (source instanceof Error) {
        return { message: source.message };
    }
    if (typeof source === 'string' && source.trim() !== '') {
        return { message: source };
    }
    return { message: 'The meeting store rejected the request.' };
}

/**
 * A `MeetingProblem` as a real `Error`, so `get`/`list`/`update`/`cancel`/
 * `join` can throw and surface through TanStack Query's `error` channel
 * (unlike `createInstantMeeting`/`scheduleMeeting`, whose modals need a
 * non-throwing `{ data, error }` result to stay open on failure).
 */
export class MeetingApiError extends Error {
    code?: string;
    traceId?: string;
    status?: number;

    constructor(problem: MeetingProblem) {
        super(problem.message);
        this.name = 'MeetingApiError';
        this.code = problem.code;
        this.traceId = problem.traceId;
        this.status = problem.status;
    }
}

function toMeetingError(source: unknown): MeetingApiError {
    return new MeetingApiError(toMeetingProblem(source));
}

/** Calls a resolver function, wrapping any rejection as a `MeetingApiError`. */
async function invokeMeeting<T>(
    functionKey: string,
    payload: Record<string, unknown> = {},
): Promise<T> {
    try {
        return (await invoke(functionKey, payload)) as T;
    } catch (error) {
        throw toMeetingError(error);
    }
}

/**
 * Create + start an instant meeting (UC01) via the `createInstantMeeting`
 * resolver function (Forge KVS-backed — see `app/src/meetingStore.ts`).
 * There is no mock fallback: a rejected resolver call surfaces as `error` in
 * the `{ data, error }` result the create-meeting modal renders.
 */
export async function createInstantMeeting(
    input: CreateInstantMeetingInput,
): Promise<CreateInstantMeetingResult> {
    try {
        const data = await invokeMeeting<Meeting>(
            'createInstantMeeting',
            buildInstantMeetingPayload(
                input,
                getDeviceId(),
            ) as unknown as Record<string, unknown>,
        );
        return { data };
    } catch (error) {
        return { error: toMeetingProblem(error) };
    }
}

/**
 * Create a scheduled meeting (UC03) via the `scheduleMeeting` resolver
 * function. There is no mock fallback: a rejected resolver call surfaces as
 * `error` in the `{ data, error }` result the schedule form renders.
 */
export async function scheduleMeeting(
    input: ScheduleMeetingInput,
): Promise<ScheduleMeetingResult> {
    try {
        const data = await invokeMeeting<Meeting>(
            'scheduleMeeting',
            buildScheduleMeetingPayload(input) as unknown as Record<
                string,
                unknown
            >,
        );
        return { data };
    } catch (error) {
        return { error: toMeetingProblem(error) };
    }
}

/** A meeting's full detail plus its distinct joined-participant roster. */
export interface MeetingDetail {
    meeting: Meeting;
    participants: Participant[];
}

/** Fetches a meeting's full detail, including participants. */
export async function getMeeting(meetingId: string): Promise<MeetingDetail> {
    return invokeMeeting<MeetingDetail>('getMeeting', { meetingId });
}

/** Lists meetings linked to a Jira issue. */
export async function listIssueMeetings(issueKey: string): Promise<Meeting[]> {
    return invokeMeeting<Meeting[]>('getIssueMeetings', { issueKey });
}

/** Lists meetings across a project for the dashboard table. */
export async function listProjectMeetings(
    filters: MeetingListFilters,
): Promise<Meeting[]> {
    return invokeMeeting<Meeting[]>(
        'getProjectMeetings',
        filters as unknown as Record<string, unknown>,
    );
}

/**
 * Build the full-replace update request body from the edit form's input.
 * `title`/`description`/`startTime` come from the edit form; `issueLink`/
 * `settings`/`zoneId`/`endTime` are carried forward unchanged from
 * `input.detail` (the meeting's full detail, fetched separately, since this
 * form doesn't edit them).
 */
export function buildUpdateMeetingPayload(
    input: UpdateMeetingInput,
): MeetUpdateMeetingRequest {
    return {
        title: input.title,
        description: input.description,
        issueLink: {
            issueId: input.detail.issueId,
            issueKey: input.detail.issueKey,
            projectKey: input.detail.projectKey,
        },
        settings: input.detail.settings ?? DEFAULT_MEETING_SETTINGS,
        zoneId: input.detail.zoneId ?? getLocalTimeZone(),
        timeRange: input.detail.endTime
            ? { startTime: input.startTime, endTime: input.detail.endTime }
            : undefined,
    };
}

/** Updates a meeting (full-replace — see `UpdateMeetingInput`). */
export async function updateMeeting(
    meetingId: string,
    input: UpdateMeetingInput,
): Promise<Meeting> {
    return invokeMeeting<Meeting>('updateMeeting', {
        meetingId,
        input: buildUpdateMeetingPayload(input),
    });
}

/** Cancels (soft-deletes) a meeting; rejects if it's currently RUNNING. */
export async function cancelMeeting(meetingId: string): Promise<Meeting> {
    return invokeMeeting<Meeting>('cancelMeeting', { meetingId });
}

/** Ends a RUNNING meeting (host/Edit-Meeting action). */
export async function endMeeting(meetingId: string): Promise<Meeting> {
    return invokeMeeting<Meeting>('endMeeting', { meetingId });
}

/** The joining participant's identity, required by the backend `join` contract. */
export interface JoinMeetingIdentity {
    displayName: string;
    deviceId: string;
    avatarUrl?: string;
}

export interface JoinMeetingResult {
    requestId: string;
    token: string;
    roomName: string;
}

/**
 * Joins a meeting, shared by "host starts a meeting" (`useStartMeeting`) and
 * "participant enters the room" (`useRoomToken`). The resolver resolves the
 * actual joining identity from Jira itself (see `app/src/index.ts`) — the
 * `identity` argument here is forwarded for parity with the old backend
 * contract but is not what authorizes or names the join.
 */
export async function joinMeeting(
    meetingId: string,
    identity: JoinMeetingIdentity,
): Promise<JoinMeetingResult> {
    return invokeMeeting<JoinMeetingResult>('joinMeeting', {
        meetingId,
        displayName: identity.displayName,
        deviceId: identity.deviceId,
        avatarUrl: identity.avatarUrl,
    });
}

/** Mints a LiveKit room access token for the current user + meeting. */
export async function getRoomToken(
    meetingId: string,
    identity: JoinMeetingIdentity,
): Promise<{ token: string; url: string | undefined }> {
    const result = await joinMeeting(meetingId, identity);
    return { token: result.token, url: apiConfig.liveKitUrl };
}

/** Any RUNNING meeting hosted by the current user, optionally excluding one issue. */
export async function findRunningMeetingHostedByUser(
    _accountId: string,
    excludingIssueKey?: string,
): Promise<Meeting | null> {
    return invokeMeeting<Meeting | null>('getHostConflict', {
        excludingIssueKey,
    });
}
