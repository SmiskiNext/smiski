import {
    createInstant,
    delete_ as deleteMeetingBackend,
    get,
    join,
    list,
    type MeetCreateInstantMeetingRequest,
    type MeetCreateInstantMeetingResponse,
    type MeetDeleteMeetingResponse,
    type MeetGetMeetingResponse,
    type MeetJoinMeetingResponse,
    type MeetLiveKit,
    type MeetMeetingListPage,
    type MeetProblemDetail,
    type MeetScheduleMeetingRequest,
    type MeetScheduleMeetingResponse,
    type MeetUpdateMeetingRequest,
    type MeetUpdateMeetingResponse,
    schedule,
    update,
} from '@smiskinext/smiski-ts';
import type { Meeting, MeetingStatus, Participant } from '../domain';
import { getLocalTimeZone } from '../utils/datetime';
import { apiConfig } from './config';
import { forgeRemoteClient } from './forgeRemoteFetch';
import {
    getDeviceId,
    meetingFromBackend,
    meetingsFromBackend,
    participantsFromBackend,
} from './mappers';

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
 * Edit of an existing meeting. The backend `update` operation is a full
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
 * Backend problem mapped for the create/schedule modals. Carries at least a
 * human-readable `message`; `code`, `traceId`, and `status` are surfaced when
 * the backend problem+json (RFC 9457) provides them.
 */
export interface MeetingProblem {
    message: string;
    code?: string;
    traceId?: string;
    status?: number;
}

/** SDK-native result of instant creation: the meeting, its LiveKit access, or a problem. */
export interface CreateInstantMeetingResult {
    data?: Meeting;
    livekit?: MeetLiveKit;
    error?: MeetingProblem;
}

/** SDK-native result of scheduled creation: the meeting or a problem. */
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
 * Build the instant-create request body from the form input, conforming to the
 * OpenAPI `MeetCreateInstantMeetingRequest` contract (nested `issueLink`, a
 * `settings` object, a `host` object, resolved `zoneId`, invitees). Pure and
 * free of `@forge/bridge`, so the invitee contract (each carries `email`,
 * `accountId`, `displayName`; no invitee → empty list) is unit-testable in
 * isolation. The `deviceId` is passed in so the browser-only `localStorage`
 * lookup stays out of this pure builder.
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
 * Build the scheduled-create request body from the form input, conforming to
 * the OpenAPI `MeetScheduleMeetingRequest` contract (`organizerEmail`,
 * `organizerDisplayName`, nested `issueLink`, `settings`, `timeRange`, resolved
 * `zoneId`, invitees). Carries no `host` object because the backend resolves
 * host identity from the request header. Pure and free of `@forge/bridge`, so
 * the payload contract is unit-testable in isolation, exactly like the instant
 * builder.
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
 * Create + start an instant meeting (UC01) by calling the generated SDK
 * `createInstant` operation, whose transport is bridged to Forge Remote. Forge
 * attaches a signed Forge Invocation Token (FIT) as `Authorization: Bearer`;
 * the app asserts NO tenant/account identity headers. There is no mock
 * fallback: a rejected or unreachable backend surfaces as `error` in the
 * SDK-native `{ data, error }` result the create-meeting modal renders.
 */
export async function createInstantMeeting(
    input: CreateInstantMeetingInput,
): Promise<CreateInstantMeetingResult> {
    try {
        const result = await createInstant({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion },
            body: buildInstantMeetingPayload(input, getDeviceId()),
        });
        if (result.error !== undefined) {
            return { error: toMeetingProblem(result.error) };
        }
        const response = result.data as MeetCreateInstantMeetingResponse;
        return {
            data: meetingFromBackend(response),
            livekit: response.livekit,
        };
    } catch (error) {
        return { error: toMeetingProblem(error) };
    }
}

/**
 * Create a scheduled meeting (UC03) by calling the generated SDK `schedule`
 * operation, whose transport is bridged to Forge Remote. Forge attaches a
 * signed Forge Invocation Token (FIT) as `Authorization: Bearer`; the app
 * asserts NO tenant/account identity headers and sends no `host` object (host
 * identity is resolved from the request header by the backend). There is no
 * mock fallback: a rejected or unreachable backend surfaces as `error` in the
 * SDK-native `{ data, error }` result the schedule form renders.
 */
export async function scheduleMeeting(
    input: ScheduleMeetingInput,
): Promise<ScheduleMeetingResult> {
    try {
        const result = await schedule({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion },
            body: buildScheduleMeetingPayload(input),
        });
        if (result.error !== undefined) {
            return { error: toMeetingProblem(result.error) };
        }
        const response = result.data as MeetScheduleMeetingResponse;
        return { data: meetingFromBackend(response) };
    } catch (error) {
        return { error: toMeetingProblem(error) };
    }
}

/**
 * Map an SDK failure — a backend problem+json body, a thrown network/validation
 * error, or a raw string — to the `MeetingProblem` the modals render, always
 * yielding a human-readable `message`.
 */
function toMeetingProblem(source: unknown): MeetingProblem {
    if (isProblemDetail(source)) {
        return {
            message:
                source.detail
                ?? source.title
                ?? 'The meeting backend rejected the request.',
            code: source.code,
            traceId: source.traceId,
            status: source.status,
        };
    }
    if (source instanceof Error) {
        return { message: source.message };
    }
    if (typeof source === 'string' && source.trim() !== '') {
        return { message: source };
    }
    return { message: 'The meeting backend rejected the request.' };
}

function isProblemDetail(value: unknown): value is MeetProblemDetail {
    return Boolean(
        value
            && typeof value === 'object'
            && !(value instanceof Error)
            && ('detail' in value
                || 'title' in value
                || 'code' in value
                || 'status' in value),
    );
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

/**
 * Calls an SDK operation, throwing a `MeetingApiError` for both a thrown
 * (network/validation) failure and an SDK-native `{ error }` result, or
 * returning the unwrapped `data` otherwise.
 */
async function unwrap<T>(
    call: () => Promise<{ data?: T; error?: unknown }>,
): Promise<T> {
    let result: { data?: T; error?: unknown };
    try {
        result = await call();
    } catch (error) {
        throw toMeetingError(error);
    }
    if (result.error !== undefined) throw toMeetingError(result.error);
    return result.data as T;
}

/** A meeting's full detail plus its distinct joined-participant roster. */
export interface MeetingDetail {
    meeting: Meeting;
    participants: Participant[];
}

/** Fetches a meeting's full detail (backend `get`), including participants. */
export async function getMeeting(meetingId: string): Promise<MeetingDetail> {
    const response = await unwrap<MeetGetMeetingResponse>(() =>
        get({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
        }),
    );
    return {
        meeting: meetingFromBackend(response),
        participants: participantsFromBackend(response),
    };
}

/** Lists meetings linked to a Jira issue (backend `list`, exact `issueKey` filter). */
export async function listIssueMeetings(issueKey: string): Promise<Meeting[]> {
    const response = await unwrap<MeetMeetingListPage>(() =>
        list({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion },
            body: { issueKey },
        }),
    );
    return meetingsFromBackend(response);
}

/**
 * Build the full-replace update request body from the edit form's input,
 * conforming to the OpenAPI `MeetUpdateMeetingRequest` contract. `title`/
 * `description`/`startTime` come from the edit form; `issueLink`/`settings`/
 * `zoneId`/`endTime` are carried forward unchanged from `input.detail` (the
 * meeting's full detail, fetched separately, since this form doesn't edit
 * them). Pure, so the contract is unit-testable like the instant/schedule
 * builders above.
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

/** Updates a meeting (backend `update`, full-replace — see `UpdateMeetingInput`). */
export async function updateMeeting(
    meetingId: string,
    input: UpdateMeetingInput,
): Promise<Meeting> {
    const response = await unwrap<MeetUpdateMeetingResponse>(() =>
        update({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: buildUpdateMeetingPayload(input),
        }),
    );
    return meetingFromBackend(response);
}

/** Cancels (soft-deletes) a meeting (backend `delete`; 409 if it's RUNNING). */
export async function cancelMeeting(meetingId: string): Promise<Meeting> {
    const response = await unwrap<MeetDeleteMeetingResponse>(() =>
        deleteMeetingBackend({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
        }),
    );
    return meetingFromBackend(response);
}

/** The joining participant's identity, required by the backend `join` operation. */
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
 * Joins a meeting (backend `join`), shared by "host starts a meeting"
 * (`useStartMeeting`) and "participant enters the room" (`useRoomToken`).
 * Under `MANUAL_APPROVAL` admission the response is `PENDING` with no token —
 * there is no waiting-room UI yet, so that surfaces as a clear error instead
 * of leaving the caller with a silently-missing token.
 */
export async function joinMeeting(
    meetingId: string,
    identity: JoinMeetingIdentity,
): Promise<JoinMeetingResult> {
    const response = await unwrap<MeetJoinMeetingResponse>(() =>
        join({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: {
                displayName: identity.displayName,
                deviceId: identity.deviceId,
                avatarUrl: identity.avatarUrl,
            },
        }),
    );
    if (!response.token || !response.roomName) {
        throw new MeetingApiError({
            message: 'Waiting for the host to approve your request to join.',
            code: 'JOIN_PENDING_APPROVAL',
        });
    }
    return {
        requestId: response.requestId ?? '',
        token: response.token,
        roomName: response.roomName,
    };
}

/** Mints a LiveKit room access token for the current user + meeting. */
export async function getRoomToken(
    meetingId: string,
    identity: JoinMeetingIdentity,
): Promise<{ token: string; url: string | undefined }> {
    const result = await joinMeeting(meetingId, identity);
    return { token: result.token, url: apiConfig.liveKitUrl };
}
