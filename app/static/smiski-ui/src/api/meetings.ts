import { invoke } from '@forge/bridge';
import type { Meeting, MeetingStatus } from '../domain';
import { apiRequest } from './client';
import { shouldUseBackendApi } from './config';
import { meetingEndpoints } from './endpoints';
import {
    createInstantMeetingRequest,
    type MeetingApiContext,
    meetingFromBackend,
    meetingsFromBackend,
    roomTokenFromBackend,
    scheduleMeetingRequest,
    updateMeetingRequest,
} from './mappers';

/** Payload to create an instant meeting (UC01). */
export interface CreateInstantMeetingInput {
    issueKey: string;
    issueId?: string;
    projectKey?: string;
    title: string;
    description?: string;
    zoneId?: string;
    /** Jira accountIds invited up front, if any. */
    participantAccountIds?: string[];
}

/** Payload to schedule a meeting (UC03). */
export interface ScheduleMeetingInput {
    issueKey: string;
    issueId?: string;
    projectKey?: string;
    title: string;
    /** ISO datetime. */
    startTime: string;
    /** ISO datetime. Defaults to one hour after startTime for the backend DTO. */
    endTime?: string;
    durationMinutes?: number;
    zoneId?: string;
    description?: string;
    /** Jira accountIds invited up front, if any. */
    participantAccountIds?: string[];
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

/** Create + start an instant meeting (UC01). */
export async function createInstantMeeting(
    input: CreateInstantMeetingInput,
    context?: MeetingApiContext,
): Promise<Meeting> {
    const payload = await apiRequest<unknown>(meetingEndpoints.createInstant, {
        method: 'POST',
        body: createInstantMeetingRequest(input, context),
    });
    return meetingFromBackend(payload);
}

/** Create a scheduled meeting (UC03). */
export async function scheduleMeeting(
    input: ScheduleMeetingInput,
    context?: MeetingApiContext,
): Promise<Meeting> {
    const payload = await apiRequest<unknown>(meetingEndpoints.schedule, {
        method: 'POST',
        body: scheduleMeetingRequest(input, context),
    });
    return meetingFromBackend(payload);
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
