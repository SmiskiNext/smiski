import {
    acceptJoinRequests as acceptJoinRequestsOperation,
    addInvitees,
    batchDeleteInvitees,
    batchDelete as batchDeleteOperation,
    cancel,
    createInstant,
    declineJoinRequests as declineJoinRequestsOperation,
    delete_ as deleteMeetingOperation,
    end,
    get,
    join,
    list,
    listIssueMeetings as listIssueMeetingsOperation,
    listPendingJoinRequests as listPendingJoinRequestsOperation,
    type MeetAddMeetingInviteesRequest,
    type MeetAddMeetingInviteesResponse,
    type MeetBatchDeleteMeetingsResponse,
    type MeetCancelMeetingResponse,
    type MeetCreateInstantMeetingRequest,
    type MeetCreateInstantMeetingResponse,
    type MeetDeleteMeetingResponse,
    type MeetEndMeetingResponse,
    type MeetGetMeetingResponse,
    type MeetIssueMeetingListPage,
    type MeetJoinDecisionResponse,
    type MeetJoinMeetingResponse,
    type MeetListMeetingsRequest,
    type MeetListPendingJoinRequestsResponse,
    type MeetMeetingListPage,
    type MeetProblemDetail,
    type MeetRemoveMeetingInviteesResponse,
    type MeetScheduleMeetingRequest,
    type MeetScheduleMeetingResponse,
    type MeetUpdateMeetingRequest,
    type MeetUpdateMeetingResponse,
    type MeetUpdateMeetingSettingsRequest,
    type MeetUpdateMeetingSettingsResponse,
    schedule,
    update,
    updateSettings,
} from '@smiskinext/smiski-ts';
import type {
    JoinRequestDecision,
    Meeting,
    MeetingInvitee,
    MeetingSettings,
    MeetingStatus,
    Participant,
    PendingJoinRequest,
    PendingJoinRequestsPage,
    PendingJoinRequestsPageParams,
} from '../domain';
import { getLocalTimeZone } from '../utils/datetime';
import { apiConfig } from './config';
import { forgeRemoteClient } from './forgeRemoteFetch';
import {
    getDeviceId,
    meetingFromBackend,
    meetingInviteesFromBackend,
    meetingsFromBackend,
    participantsFromBackend,
} from './mappers';
import { waitForJoinRequestDecision } from './meetingEvents';

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

/**
 * User-editable meeting settings exposed by the create forms' shared
 * "Advanced settings" section.
 */
export interface CreateMeetingSettingsInput {
    admissionPolicy: 'ALLOW_ALL' | 'MANUAL_APPROVAL';
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
}

/** Payload to create an instant meeting (UC01). */
export interface CreateInstantMeetingInput {
    issueKey: string;
    issueId: string;
    projectKey?: string;
    title: string;
    description: string;
    zoneId?: string;
    /** Full invitees carrying accountId, displayName, and email. */
    invitees?: MeetingInviteeInput[];
    /** Host identity (from CurrentUserContext); resolves host/organizer fields. */
    host?: InstantMeetingHostIdentity;
    /** Overrides DEFAULT_MEETING_SETTINGS when the host expands "Advanced settings". */
    settings?: CreateMeetingSettingsInput;
}

/** Payload to schedule a meeting (UC03). */
export interface ScheduleMeetingInput {
    issueKey: string;
    issueId: string;
    projectKey?: string;
    title: string;
    /** ISO-8601 UTC instant for the scheduled start. */
    startTime: string;
    /** ISO-8601 UTC instant for the scheduled end. */
    endTime: string;
    zoneId?: string;
    description: string;
    /** Invitees carrying full identity (accountId, displayName, email). */
    invitees: MeetingInviteeInput[];
    /** Organizer identity (from CurrentUserContext); resolves organizer fields. */
    organizer?: InstantMeetingHostIdentity;
    /** Overrides DEFAULT_MEETING_SETTINGS when the host expands "Advanced settings". */
    settings?: CreateMeetingSettingsInput;
}

/** Jira issue a meeting is linked to, as sent on a meeting information update. */
export interface MeetingIssueLinkInput {
    issueId: string;
    issueKey: string;
    projectKey: string;
}

/** Common fields accepted by the backend's full-replace update operation. */
interface UpdateMeetingBaseInput {
    title: string;
    description: string;
}

/**
 * Edit of an existing meeting. A detail-backed input carries forward fields
 * that the project-page form leaves unchanged, while the issue-panel form can
 * provide the complete editable issue/time payload explicitly. Settings stay
 * on the dedicated settings endpoint.
 */
export type UpdateMeetingInput = UpdateMeetingBaseInput &
    (
        | {
              /**
               * New scheduled start. Absent for instant meetings or when the
               * meeting has left `SCHEDULED` and the form locks its schedule.
               */
              startTime?: string;
              detail: Meeting;
              /** Preserve the current issue link when the host did not change it. */
              selectedIssue?: MeetingIssueLinkInput;
          }
        | {
              issueId: string;
              issueKey: string;
              projectKey: string;
              startTime: string;
              endTime: string;
              zoneId: string;
          }
    );

/** Filters for the project-page dashboard listing. */
export interface MeetingSearchFilters {
    projectKey?: string;
    issueKey?: string;
    createdByAccountId?: string;
    statuses?: MeetingStatus[];
    search?: string;
    sort?: MeetingListSort;
}

export interface MeetingListFilters extends MeetingSearchFilters {
    projectKey: string;
}

export type MeetingListSort = NonNullable<MeetListMeetingsRequest['sort']>;

export interface CursorPageParams {
    pageSize?: number;
    pageToken?: string;
}

export interface ProjectMeetingListParams
    extends MeetingListFilters,
        CursorPageParams {}

interface MeetingSearchPageParams
    extends MeetingSearchFilters,
        CursorPageParams {}

export interface MeetingCursorPage {
    meetings: Meeting[];
    size: number;
    hasNext: boolean;
    nextPageToken?: string;
}

export interface OffsetPageParams {
    offset?: number;
    pageSize?: number;
}

export interface MeetingOffsetPage {
    meetings: Meeting[];
    total: number;
    offset: number;
    pageSize: number;
    hasNext: boolean;
}

export const DEFAULT_MEETING_PAGE_SIZE = 20;
export const MAX_MEETING_PAGE_SIZE = 50;

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

/** SDK-native result of instant creation: the meeting, or a problem. */
export interface CreateInstantMeetingResult {
    data?: Meeting;
    error?: MeetingProblem;
}

/** SDK-native result of scheduled creation: the meeting or a problem. */
export interface ScheduleMeetingResult {
    data?: Meeting;
    error?: MeetingProblem;
}

/**
 * Default meeting settings shared by the instant and scheduled create flows.
 * `admissionPolicy` must be one of the backend's `AdmissionPolicy` enum
 * values (`ALLOW_ALL` | `MANUAL_APPROVAL`) — the backend does
 * `AdmissionPolicy.valueOf(...)` on this string with no fallback, so any
 * other value throws.
 */
const DEFAULT_MEETING_SETTINGS = {
    admissionPolicy: 'ALLOW_ALL',
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
        description: input.description.trim(),
        issueLink: {
            issueId: input.issueId,
            issueKey: input.issueKey,
            projectKey,
        },
        settings: { ...DEFAULT_MEETING_SETTINGS, ...input.settings },
        host: {
            displayName: input.host?.displayName ?? 'Jira user',
            deviceId,
            avatarUrl: input.host?.avatarUrl,
        },
        organizerEmail: input.host?.email?.trim() ?? '',
        organizerDisplayName: input.host?.displayName ?? 'Jira user',
        zoneId: input.zoneId ?? getLocalTimeZone(),
        invitees: (input.invitees ?? []).map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email?.trim() ?? '',
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
        description: input.description.trim(),
        issueLink: {
            issueId: input.issueId,
            issueKey: input.issueKey,
            projectKey,
        },
        settings: { ...DEFAULT_MEETING_SETTINGS, ...input.settings },
        timeRange: {
            startTime: input.startTime,
            endTime: input.endTime,
        },
        organizerEmail: input.organizer?.email?.trim() ?? '',
        organizerDisplayName: input.organizer?.displayName ?? 'Jira user',
        zoneId: input.zoneId ?? getLocalTimeZone(),
        invitees: input.invitees.map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email?.trim() ?? '',
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
        return { data: meetingFromBackend(response) };
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

/** A meeting's full detail with active invitees and joined participants. */
export interface MeetingDetail {
    meeting: Meeting;
    invitees: MeetingInvitee[];
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
    const participants = participantsFromBackend(response);
    return {
        meeting: meetingFromBackend(response, {
            participantCount: participants.length,
        }),
        invitees: meetingInviteesFromBackend(response),
        participants,
    };
}

/** Builds the exact body accepted by the atomic add-invitees endpoint. */
export function buildAddMeetingInviteesPayload(
    invitees: MeetingInviteeInput[],
): MeetAddMeetingInviteesRequest {
    return {
        invitees: invitees.map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email,
        })),
    };
}

/** Adds invitees to a SCHEDULED or RUNNING meeting as its host. */
export async function addMeetingInvitees(
    meetingId: string,
    invitees: MeetingInviteeInput[],
): Promise<MeetingInvitee[]> {
    const response = await unwrap<MeetAddMeetingInviteesResponse>(() =>
        addInvitees({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: buildAddMeetingInviteesPayload(invitees),
        }),
    );
    return meetingInviteesFromBackend(response);
}

/** Removes active invitees by invitee id from a SCHEDULED or RUNNING meeting. */
export async function removeMeetingInvitees(
    meetingId: string,
    inviteeIds: string[],
): Promise<MeetingInvitee[]> {
    const response = await unwrap<MeetRemoveMeetingInviteesResponse>(() =>
        batchDeleteInvitees({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: { inviteeIds },
        }),
    );
    return meetingInviteesFromBackend(response);
}

/** Lists one offset-paginated page linked to a Jira issue id. */
export async function listIssueMeetings(
    issueId: string,
    params: OffsetPageParams = {},
): Promise<MeetingOffsetPage> {
    const response = await unwrap<MeetIssueMeetingListPage>(() =>
        listIssueMeetingsOperation({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, issueId },
            body: params,
        }),
    );
    const meetings = meetingsFromBackend(response);
    const offset = response.meta?.offset ?? params.offset ?? 0;
    const pageSize =
        response.meta?.pageSize ?? params.pageSize ?? DEFAULT_MEETING_PAGE_SIZE;
    const total = response.meta?.total ?? offset + meetings.length;
    const hasNext =
        meetings.length > 0
        && (response.meta?.hasNext ?? offset + meetings.length < total);
    return { meetings, total, offset, pageSize, hasNext };
}

/** Lists a page of PENDING join requests for the meeting host. */
export async function listPendingMeetingJoinRequests(
    meetingId: string,
    params: PendingJoinRequestsPageParams = {},
): Promise<PendingJoinRequestsPage> {
    const response = await unwrap<MeetListPendingJoinRequestsResponse>(() =>
        listPendingJoinRequestsOperation({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            query: params,
        }),
    );
    const requests: PendingJoinRequest[] = (response.results ?? []).map(
        (request) => ({
            requestId: request.requestId ?? '',
            accountId: request.accountId ?? '',
            displayName:
                request.displayName ?? request.accountId ?? 'Jira user',
            status: 'PENDING',
            requestedAt: request.requestedAt ?? '',
            expiresAt: request.expiresAt ?? '',
            avatarUrl: '',
        }),
    );
    return {
        requests,
        total: response.meta?.total ?? requests.length,
        offset: response.meta?.offset ?? params.offset ?? 0,
        pageSize: response.meta?.pageSize ?? params.pageSize ?? 20,
    };
}

function joinRequestDecisionsFromBackend(
    response: MeetJoinDecisionResponse,
): JoinRequestDecision[] {
    return (response.results ?? []).map((decision) => {
        if (
            decision.status !== 'APPROVED'
            && decision.status !== 'DENIED'
            && decision.status !== 'FAILED'
        ) {
            throw new MeetingApiError({
                message:
                    'The meeting backend returned an invalid join decision.',
                code: 'INVALID_JOIN_DECISION_RESPONSE',
            });
        }
        return {
            requestId: decision.requestId ?? '',
            status: decision.status,
            token: decision.token ?? null,
            roomName: decision.roomName ?? null,
            reason: decision.reason ?? null,
        };
    });
}

/** Accepts one or more PENDING requests as the meeting host. */
export async function acceptPendingMeetingJoinRequests(
    meetingId: string,
    requestIds: string[],
): Promise<JoinRequestDecision[]> {
    const response = await unwrap<MeetJoinDecisionResponse>(() =>
        acceptJoinRequestsOperation({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: { requestIds },
        }),
    );
    return joinRequestDecisionsFromBackend(response);
}

/** Declines one or more PENDING requests as the meeting host. */
export async function declinePendingMeetingJoinRequests(
    meetingId: string,
    requestIds: string[],
): Promise<JoinRequestDecision[]> {
    const response = await unwrap<MeetJoinDecisionResponse>(() =>
        declineJoinRequestsOperation({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: { requestIds },
        }),
    );
    return joinRequestDecisionsFromBackend(response);
}

async function listMeetingsPage(
    params: MeetingSearchPageParams,
): Promise<MeetingCursorPage> {
    const response = await unwrap<MeetMeetingListPage>(() =>
        list({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion },
            body: {
                projectKey: params.projectKey,
                issueKey: params.issueKey,
                creatorId: params.createdByAccountId,
                statuses: params.statuses?.length ? params.statuses : undefined,
                search: params.search,
                sort: params.sort,
                pageSize: params.pageSize,
                pageToken: params.pageToken,
            },
        }),
    );
    const meetings = meetingsFromBackend(response);
    const hasNext = response.meta?.hasNext ?? false;
    const nextPageToken = response.meta?.nextPageToken;
    if (hasNext && !nextPageToken) {
        throw new MeetingApiError({
            message:
                'The meeting backend returned an invalid pagination response.',
            code: 'INVALID_PAGINATION_RESPONSE',
        });
    }
    return {
        meetings,
        size: response.meta?.size ?? meetings.length,
        hasNext,
        nextPageToken,
    };
}

/** Lists one cursor-paginated project page with server-side filtering/sort. */
export function listProjectMeetings(
    params: ProjectMeetingListParams,
): Promise<MeetingCursorPage> {
    return listMeetingsPage(params);
}

/**
 * Loads every cursor page for correctness-sensitive checks such as scheduling
 * and host conflicts. Repeated cursors are rejected instead of looping.
 */
export async function listAllMeetings(
    filters: MeetingSearchFilters,
): Promise<Meeting[]> {
    const meetings: Meeting[] = [];
    const seenTokens = new Set<string>();
    let pageToken: string | undefined;

    do {
        const page = await listMeetingsPage({
            ...filters,
            pageSize: MAX_MEETING_PAGE_SIZE,
            pageToken,
        });
        meetings.push(...page.meetings);
        if (!page.hasNext) return meetings;

        const nextPageToken = page.nextPageToken;
        if (!nextPageToken) {
            throw new MeetingApiError({
                message:
                    'The meeting backend returned an invalid pagination response.',
                code: 'INVALID_PAGINATION_RESPONSE',
            });
        }
        if (seenTokens.has(nextPageToken)) {
            throw new MeetingApiError({
                message: 'The meeting backend returned a repeated page cursor.',
                code: 'INVALID_PAGINATION_RESPONSE',
            });
        }
        seenTokens.add(nextPageToken);
        pageToken = nextPageToken;
    } while (pageToken);

    return meetings;
}

/** Build a full-replace backend payload from either edit surface's input. */
export function buildUpdateMeetingPayload(
    input: UpdateMeetingInput,
): MeetUpdateMeetingRequest {
    if (!('detail' in input)) {
        return {
            title: input.title,
            description: input.description,
            issueLink: {
                issueId: input.issueId,
                issueKey: input.issueKey,
                projectKey: input.projectKey,
            },
            zoneId: input.zoneId,
            timeRange: {
                startTime: input.startTime,
                endTime: input.endTime,
            },
        };
    }

    const issueLink = input.selectedIssue ?? {
        issueId: input.detail.issueId,
        issueKey: input.detail.issueKey,
        projectKey: input.detail.projectKey,
    };
    const startTime = input.startTime ?? input.detail.scheduledAt;
    return {
        title: input.title,
        description: input.description,
        issueLink,
        zoneId: input.detail.zoneId ?? getLocalTimeZone(),
        timeRange:
            startTime && input.detail.endTime
                ? { startTime, endTime: input.detail.endTime }
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

/**
 * Replaces a meeting's settings (backend `updateSettings`, host-only — 403
 * otherwise). Separate from `updateMeeting`, which no longer carries
 * settings. Wired to `MeetingSettingsModal` via `useUpdateMeetingSettings`.
 *
 * Returns the settings block directly rather than routing the response
 * through `meetingFromBackend`: `MeetUpdateMeetingSettingsResponse` is a
 * flat `{ meetingId, admissionPolicy, ... }` shape, not a full meeting
 * snapshot (no `id`/`title`/`status`/nested `settings`), so mapping it as
 * one would silently produce a near-empty `Meeting`.
 */
export async function updateMeetingSettings(
    meetingId: string,
    settings: MeetingSettings,
): Promise<MeetingSettings> {
    const response = await unwrap<MeetUpdateMeetingSettingsResponse>(() =>
        updateSettings({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
            body: settings satisfies MeetUpdateMeetingSettingsRequest,
        }),
    );
    return {
        admissionPolicy: response.admissionPolicy ?? settings.admissionPolicy,
        maxParticipants: response.maxParticipants ?? settings.maxParticipants,
        allowScreenShare:
            response.allowScreenShare ?? settings.allowScreenShare,
        chatEnabled: response.chatEnabled ?? settings.chatEnabled,
        allowMicrophone: response.allowMicrophone ?? settings.allowMicrophone,
        allowVideo: response.allowVideo ?? settings.allowVideo,
    };
}

/**
 * Cancels a SCHEDULED meeting (backend `cancel`; SCHEDULED-only — 409
 * otherwise). Sets `status: CANCELED`/`cancelReason: HOST_CANCELED` and
 * publishes `MeetingCanceledEvent`, which triggers a cancellation email to
 * invitees.
 */
export async function cancelMeeting(meetingId: string): Promise<Meeting> {
    const response = await unwrap<MeetCancelMeetingResponse>(() =>
        cancel({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
        }),
    );
    return meetingFromBackend(response);
}

/**
 * Ends a RUNNING meeting (host/Edit-Meeting action; backend `end`).
 * Transitions the meeting to COMPLETED, closes participation logs, and
 * requests best-effort LiveKit room deletion.
 */
export async function endMeeting(meetingId: string): Promise<Meeting> {
    const response = await unwrap<MeetEndMeetingResponse>(() =>
        end({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
        }),
    );
    return meetingFromBackend(response);
}

/** Soft-deletes one meeting as its host. RUNNING meetings are rejected. */
export async function deleteMeeting(meetingId: string): Promise<string> {
    const response = await unwrap<MeetDeleteMeetingResponse>(() =>
        deleteMeetingOperation({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion, id: meetingId },
        }),
    );
    return response.meeting?.id ?? meetingId;
}

/**
 * Soft-deletes a batch of meetings by ID (backend `batchDelete`).
 */
export async function batchDeleteMeetings(
    meetingIds: string[],
): Promise<Meeting[]> {
    const response = await unwrap<MeetBatchDeleteMeetingsResponse>(() =>
        batchDeleteOperation({
            client: forgeRemoteClient,
            path: { version: apiConfig.apiVersion },
            body: { meetingIds },
        }),
    );
    return meetingsFromBackend(response);
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

export interface JoinMeetingOptions {
    signal?: AbortSignal;
    onPending?: (requestId: string) => void;
}

type JoinAttempt =
    | ({ status: 'APPROVED' } & JoinMeetingResult)
    | { status: 'PENDING'; requestId: string };

function joinAttemptFromBackend(
    response: MeetJoinMeetingResponse,
): JoinAttempt {
    const requestId = response.requestId ?? '';
    if (response.status === 'APPROVED' && response.token && response.roomName) {
        return {
            status: 'APPROVED',
            requestId,
            token: response.token,
            roomName: response.roomName,
        };
    }
    if (response.status === 'PENDING' && requestId) {
        return { status: 'PENDING', requestId };
    }
    throw new MeetingApiError({
        message: 'The meeting backend returned an invalid join response.',
        code: 'INVALID_JOIN_RESPONSE',
    });
}

/**
 * Joins a meeting (backend `join`), shared by "host starts a meeting"
 * (`useStartMeeting`) and "participant enters the room" (`useRoomToken`).
 * Under `MANUAL_APPROVAL`, waits on the request-scoped SSE stream and returns
 * only after the host approves. The LiveKit token therefore never reaches the
 * room client before admission succeeds.
 */
export async function joinMeeting(
    meetingId: string,
    identity: JoinMeetingIdentity,
    options: JoinMeetingOptions = {},
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
    const attempt = joinAttemptFromBackend(response);
    if (attempt.status === 'APPROVED') {
        return {
            requestId: attempt.requestId,
            token: attempt.token,
            roomName: attempt.roomName,
        };
    }

    options.onPending?.(attempt.requestId);
    const signal = options.signal ?? new AbortController().signal;
    const decision = await waitForJoinRequestDecision(
        meetingId,
        attempt.requestId,
        signal,
    );
    if (decision.status === 'DENIED') {
        throw new MeetingApiError({
            message: decision.reason ?? 'The host declined your join request.',
            code: 'JOIN_REQUEST_DENIED',
        });
    }
    return {
        requestId: attempt.requestId,
        token: decision.token,
        roomName: decision.roomName,
    };
}

/** Mints a LiveKit room access token for the current user + meeting. */
export async function getRoomToken(
    meetingId: string,
    identity: JoinMeetingIdentity,
    options: JoinMeetingOptions = {},
): Promise<{ token: string; url: string | undefined }> {
    const result = await joinMeeting(meetingId, identity, options);
    return { token: result.token, url: apiConfig.liveKitUrl };
}

/**
 * Any RUNNING meeting hosted by `accountId`, optionally excluding one issue.
 * Every cursor page is checked so a conflict cannot be missed after page 1.
 * The backend has no "exclude an issue" filter, so that part stays client-side.
 */
export async function findRunningMeetingHostedByUser(
    accountId: string,
    excludingIssueKey?: string,
): Promise<Meeting | null> {
    const running = await listAllMeetings({
        createdByAccountId: accountId,
        statuses: ['RUNNING'],
    });
    return (
        running.find((meeting) => meeting.issueKey !== excludingIssueKey)
        ?? null
    );
}
