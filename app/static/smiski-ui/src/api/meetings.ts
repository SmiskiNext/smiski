import { invoke, requestRemote } from '@forge/bridge';
import type { Meeting, MeetingStatus } from '../domain';
import { getLocalTimeZone } from '../utils/datetime';
import { apiRequest } from './client';
import { shouldUseBackendApi } from './config';
import { meetingEndpoints } from './endpoints';
import {
    getDeviceId,
    type MeetingApiContext,
    meetingFromBackend,
    meetingsFromBackend,
    roomTokenFromBackend,
    updateMeetingRequest,
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
}

/** Partial edit of an existing scheduled meeting. */
export interface UpdateMeetingInput {
    title?: string;
    description?: string;
    startTime?: string;
    endTime?: string;
    durationMinutes?: number;
    zoneId?: string;
    issueKey?: string;
    issueId?: string;
    projectKey?: string;
    baseMeeting?: Meeting;
}

/** Filters for the project-page dashboard listing. */
export interface MeetingListFilters {
    projectKey: string;
    issueKey?: string;
    createdByAccountId?: string;
    status?: MeetingStatus;
    search?: string;
}

/** List meetings bound to a single Issue (UC02/UC07). */
export async function getIssueMeetings(issueKey: string): Promise<Meeting[]> {
    const payload = await apiRequest<unknown>(meetingEndpoints.list, {
        query: { issueKey },
    });
    return meetingsFromBackend(payload);
}

/** List/search meetings across a project (dashboard). */
export async function getProjectMeetings(
    filters: MeetingListFilters,
): Promise<Meeting[]> {
    const payload = await apiRequest<unknown>(meetingEndpoints.list, {
        query: { ...filters },
    });
    return meetingsFromBackend(payload);
}

/** Fetch a single meeting by id. */
export async function getMeeting(meetingId: string): Promise<Meeting> {
    const payload = await apiRequest<unknown>(meetingEndpoints.byId(meetingId));
    return meetingFromBackend(payload);
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

/** The JSON body the `meet` backend's instant-create endpoint expects. */
export interface InstantMeetingInvokePayload {
    title: string;
    description: string;
    issueKey: string;
    issueId?: string;
    projectKey: string;
    zoneId: string;
    host: {
        displayName: string;
        deviceId: string;
        avatarUrl?: string;
    };
    organizerEmail: string;
    organizerDisplayName: string;
    invitees: MeetingInviteeInput[];
}

/**
 * Build the backend request body from the form input. Pure and free of
 * `@forge/bridge`, so the invitee contract (each carries `email`, `accountId`,
 * `displayName`; no invitee → empty list) is unit-testable in isolation. The
 * `deviceId` is passed in so the browser-only `localStorage` lookup stays out
 * of this pure builder.
 */
export function buildInstantMeetingPayload(
    input: CreateInstantMeetingInput,
    deviceId: string,
): InstantMeetingInvokePayload {
    const projectKey = input.projectKey ?? input.issueKey.split('-')[0];
    return {
        title: input.title,
        description: input.description?.trim() || input.title,
        issueKey: input.issueKey,
        issueId: input.issueId,
        projectKey,
        zoneId: input.zoneId ?? getLocalTimeZone(),
        host: {
            displayName: input.host?.displayName ?? 'Jira user',
            deviceId,
            avatarUrl: input.host?.avatarUrl,
        },
        organizerEmail: input.host?.email ?? '',
        organizerDisplayName: input.host?.displayName ?? 'Jira user',
        invitees: (input.invitees ?? []).map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email,
        })),
    };
}

/**
 * The JSON body the `meet` backend's scheduled-create endpoint expects. Carries
 * no `host` object because the backend resolves host identity from the request
 * header (see the `create-schedule-meeting` spec).
 */
export interface ScheduleMeetingInvokePayload {
    title: string;
    description: string;
    issueLink: {
        issueId?: string;
        issueKey: string;
        projectKey: string;
    };
    settings: typeof DEFAULT_MEETING_SETTINGS;
    timeRange: {
        startTime: string;
        endTime: string;
    };
    zoneId: string;
    invitees: MeetingInviteeInput[];
}

/**
 * Build the scheduled-create request body from the form input. Pure and free of
 * `@forge/bridge`, so the payload contract (no `host`; `description` defaults to
 * `title`; each invitee carries `email`/`accountId`/`displayName`; empty list
 * when none) is unit-testable in isolation, exactly like the instant builder.
 */
export function buildScheduleMeetingPayload(
    input: ScheduleMeetingInput,
): ScheduleMeetingInvokePayload {
    const projectKey = input.projectKey ?? input.issueKey.split('-')[0];
    return {
        title: input.title,
        description: input.description?.trim() || input.title,
        issueLink: {
            issueId: input.issueId,
            issueKey: input.issueKey,
            projectKey,
        },
        settings: DEFAULT_MEETING_SETTINGS,
        timeRange: {
            startTime: input.startTime,
            endTime: input.endTime,
        },
        zoneId: input.zoneId ?? getLocalTimeZone(),
        invitees: input.invitees.map((invitee) => ({
            accountId: invitee.accountId,
            displayName: invitee.displayName,
            email: invitee.email,
        })),
    };
}

/** Manifest `remotes` key for the `meet` backend (see app/manifest.yml). */
const MEET_REMOTE_KEY = 'meet-backend';

/**
 * Backend path for instant creation, appended to the remote `baseUrl` by
 * `requestRemote`. Mirrors the `meet` controller route (`/api/1/meetings:instant`)
 * and the manifest `endpoint.route.path`.
 */
const INSTANT_MEETING_PATH = '/api/1/meetings:instant';

/**
 * Backend path for scheduled creation, appended to the remote `baseUrl` by
 * `requestRemote`. Mirrors the `meet` controller route
 * (`/api/1/meetings:schedule`) per the `create-schedule-meeting` spec.
 */
const SCHEDULE_MEETING_PATH = '/api/1/meetings:schedule';

/** RFC 7807 problem+json shape the backend returns on a rejected request. */
interface InstantMeetingProblem {
    detail?: string;
    title?: string;
    code?: string;
    traceId?: string;
}

/** An error carrying the backend problem details for the create-meeting modal. */
type InstantMeetingError = Error & { code?: string; traceId?: string };

async function readProblem(response: Response): Promise<InstantMeetingProblem> {
    try {
        return (await response.json()) as InstantMeetingProblem;
    } catch {
        return {};
    }
}

function instantMeetingError(
    problem: InstantMeetingProblem,
): InstantMeetingError {
    const message =
        problem.detail
        ?? problem.title
        ?? 'The meeting backend rejected the instant-create request.';
    const error = new Error(message) as InstantMeetingError;
    error.code = problem.code;
    error.traceId = problem.traceId;
    return error;
}

function scheduleMeetingError(
    problem: InstantMeetingProblem,
): InstantMeetingError {
    const message =
        problem.detail
        ?? problem.title
        ?? 'The meeting backend rejected the scheduled-create request.';
    const error = new Error(message) as InstantMeetingError;
    error.code = problem.code;
    error.traceId = problem.traceId;
    return error;
}

/**
 * Create + start an instant meeting (UC01) by calling the `meet` backend
 * directly from Custom UI through Forge Remote. Forge attaches a signed Forge
 * Invocation Token (FIT) as `Authorization: Bearer`; the app asserts NO
 * tenant/account identity headers (deriving identity from the FIT is the
 * gateway's job). There is no mock fallback: a non-2xx response or unreachable
 * backend surfaces as an error the create-meeting modal renders.
 */
export async function createInstantMeeting(
    input: CreateInstantMeetingInput,
): Promise<Meeting> {
    const payload = buildInstantMeetingPayload(input, getDeviceId());
    const response = await requestRemote(MEET_REMOTE_KEY, {
        path: INSTANT_MEETING_PATH,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
        },
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
        throw instantMeetingError(await readProblem(response));
    }
    return meetingFromBackend(await response.json());
}

/**
 * Create a scheduled meeting (UC03) by calling the `meet` backend directly from
 * Custom UI through Forge Remote. Forge attaches a signed Forge Invocation Token
 * (FIT) as `Authorization: Bearer`; the app asserts NO tenant/account identity
 * headers and sends no `host` object (host identity is resolved from the request
 * header by the backend). There is no mock fallback: a non-2xx response or
 * unreachable backend surfaces as an error the schedule form renders.
 */
export async function scheduleMeeting(
    input: ScheduleMeetingInput,
): Promise<Meeting> {
    const payload = buildScheduleMeetingPayload(input);
    const response = await requestRemote(MEET_REMOTE_KEY, {
        path: SCHEDULE_MEETING_PATH,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
        },
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
        throw scheduleMeetingError(await readProblem(response));
    }
    return meetingFromBackend(await response.json());
}

/** Edit a scheduled meeting (requires EDIT_MEETING). */
export async function updateMeeting(
    meetingId: string,
    input: UpdateMeetingInput,
    context?: MeetingApiContext,
): Promise<Meeting> {
    const payload = await apiRequest<unknown>(
        meetingEndpoints.update(meetingId),
        {
            method: 'PUT',
            body: updateMeetingRequest(input, context),
        },
    );
    return meetingFromBackend(payload);
}

/** Cancel a scheduled meeting (requires EDIT_MEETING). */
export async function cancelMeeting(meetingId: string): Promise<void> {
    await apiRequest<unknown>(meetingEndpoints.cancel(meetingId), {
        method: 'POST',
    });
}

/** Transition a scheduled meeting to RUNNING (start). */
export async function startMeeting(meetingId: string): Promise<Meeting> {
    const payload = await apiRequest<unknown>(
        meetingEndpoints.start(meetingId),
        { method: 'POST' },
    );
    return payload == null
        ? getMeeting(meetingId)
        : meetingFromBackend(payload);
}

/** Transition a running meeting to COMPLETED. */
export async function endMeeting(meetingId: string): Promise<Meeting> {
    const payload = await apiRequest<unknown>(meetingEndpoints.end(meetingId), {
        method: 'POST',
    });
    return payload == null
        ? getMeeting(meetingId)
        : meetingFromBackend(payload);
}

/** Mint a LiveKit room access token for the current user + meeting. */
export async function getRoomToken(
    meetingId: string,
): Promise<{ token: string; url: string }> {
    if (shouldUseBackendApi()) {
        const payload = await apiRequest<unknown>(
            meetingEndpoints.roomToken(meetingId),
            {
                method: 'POST',
            },
        );
        return roomTokenFromBackend(payload);
    }
    return invoke('getRoomToken', { meetingId }) as Promise<{
        token: string;
        url: string;
    }>;
}

/** Any RUNNING meeting hosted by the current user, optionally excluding one issue. */
export async function findRunningMeetingHostedByUser(
    accountId: string,
    excludingIssueKey?: string,
): Promise<Meeting | null> {
    const payload = await apiRequest<unknown>(meetingEndpoints.hostConflict, {
        query: { accountId, excludingIssueKey },
    });
    const meetings = meetingsFromBackend(payload);
    if (meetings.length > 0) return meetings[0];
    if (payload == null) return null;
    const meeting = meetingFromBackend(payload);
    return meeting.id ? meeting : null;
}
