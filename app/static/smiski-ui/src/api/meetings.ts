/**
 * Meetings API — typed client signatures for the `meet` service via Kong (STUB).
 * No fetch logic; every function throws until implemented.
 *
 * Exception: `getRoomToken` calls the Forge resolver directly via
 * `@forge/bridge`'s `invoke()` instead of Kong — a temporary prototype shim
 * (see `src/index.ts`'s `getRoomToken` resolver) since the real `meet`
 * service doesn't exist yet. Replace with a Kong fetch call once it does.
 */
import { invoke } from '@forge/bridge';
import type { Meeting, MeetingStatus } from '../domain';

/** Payload to create an instant meeting (UC01). */
export interface CreateInstantMeetingInput {
  issueKey: string;
  title: string;
  description?: string;
  /** Jira accountIds invited up front, if any. */
  participantAccountIds?: string[];
}

/** Payload to schedule a meeting (UC03). */
export interface ScheduleMeetingInput {
  issueKey: string;
  title: string;
  /** ISO datetime. */
  startTime: string;
  description?: string;
  /** Jira accountIds invited up front, if any. */
  participantAccountIds?: string[];
}

/** Partial edit of an existing scheduled meeting. */
export interface UpdateMeetingInput {
  title?: string;
  description?: string;
  startTime?: string;
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
export async function getIssueMeetings(_issueKey: string): Promise<Meeting[]> {
  throw new Error('Not implemented: getIssueMeetings');
}

/** List/search meetings across a project (dashboard). */
export async function getProjectMeetings(_filters: MeetingListFilters): Promise<Meeting[]> {
  throw new Error('Not implemented: getProjectMeetings');
}

/** Fetch a single meeting by id. */
export async function getMeeting(_meetingId: string): Promise<Meeting> {
  throw new Error('Not implemented: getMeeting');
}

/** Create + start an instant meeting (UC01). */
export async function createInstantMeeting(_input: CreateInstantMeetingInput): Promise<Meeting> {
  throw new Error('Not implemented: createInstantMeeting');
}

/** Create a scheduled meeting (UC03). */
export async function scheduleMeeting(_input: ScheduleMeetingInput): Promise<Meeting> {
  throw new Error('Not implemented: scheduleMeeting');
}

/** Edit a scheduled meeting (requires EDIT_MEETING). */
export async function updateMeeting(
  _meetingId: string,
  _input: UpdateMeetingInput,
): Promise<Meeting> {
  throw new Error('Not implemented: updateMeeting');
}

/** Cancel a scheduled meeting (requires EDIT_MEETING). */
export async function cancelMeeting(_meetingId: string): Promise<void> {
  throw new Error('Not implemented: cancelMeeting');
}

/** Transition a scheduled meeting to RUNNING (start). */
export async function startMeeting(_meetingId: string): Promise<Meeting> {
  throw new Error('Not implemented: startMeeting');
}

/** Transition a running meeting to COMPLETED. */
export async function endMeeting(_meetingId: string): Promise<Meeting> {
  throw new Error('Not implemented: endMeeting');
}

/** Mint a LiveKit room access token for the current user + meeting. */
export async function getRoomToken(meetingId: string): Promise<{ token: string; url: string }> {
  return invoke('getRoomToken', { meetingId }) as Promise<{ token: string; url: string }>;
}
