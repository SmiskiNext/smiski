/**
 * In-memory mock "database" for frontend-only development.
 *
 * Function names/signatures deliberately mirror `src/api/*.ts` (the real,
 * still-throwing Kong Gateway client). Swapping a hook from this module to
 * `../api/*` later is a one-line import change — see hooks/*.ts for the
 * exact swap points, each marked with a TODO.
 *
 * State is a module-level mutable array mirrored to `localStorage`, so
 * mutations (start/cancel/schedule/record) survive page reloads / re-opening
 * the Forge modal iframe — still no backend, just a slightly stickier mock.
 */

import type {
    CreateInstantMeetingInput,
    MeetingListFilters,
    ScheduleMeetingInput,
    UpdateMeetingInput,
} from '../api/meetings';
import type { Meeting, Participant, ProjectMember, Recording } from '../domain';
import { INITIAL_MOCK_MEETINGS } from './meetings';
import {
    getMockParticipants,
    migrateMockParticipantIdentity,
    setMockParticipants,
} from './participants';
import { getMockRecording, MOCK_RECORDINGS } from './recordings';
import { CURRENT_USER } from './users';

const NETWORK_DELAY_MS = 350;
const MEETINGS_STORAGE_KEY = 'smiski:mock-meetings';

function delay<T>(value: T, ms = NETWORK_DELAY_MS): Promise<T> {
    return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

// Seed fixtures reference fake issue keys (SMISKI-101, ...) that only exist in
// standalone `vite dev`. A real deployed install has no such issues, so
// seeding them there would show meetings bound to nonexistent Jira issues —
// start empty instead and let real, issue-bound meetings accumulate.
function seedMeetings(): Meeting[] {
    return import.meta.env.DEV ? [...INITIAL_MOCK_MEETINGS] : [];
}

function loadMeetings(): Meeting[] {
    try {
        const raw = localStorage.getItem(MEETINGS_STORAGE_KEY);
        const parsed = raw ? JSON.parse(raw) : null;
        return Array.isArray(parsed) && parsed.length > 0
            ? parsed
            : seedMeetings();
    } catch {
        return seedMeetings();
    }
}

function persistMeetings(value: Meeting[]): void {
    try {
        localStorage.setItem(MEETINGS_STORAGE_KEY, JSON.stringify(value));
    } catch {
        // Best-effort only (private browsing / storage quota) — falls back to
        // in-memory-for-this-tab behavior, same as before this existed.
    }
}

let meetings: Meeting[] = loadMeetings();
const recordings: Record<string, Recording> = { ...MOCK_RECORDINGS };
// Derived from persisted state so ids never collide after a reload.
let nextId = meetings.reduce((max, m) => {
    const n = Number(m.id.split('-').pop());
    return Number.isFinite(n) && n > max ? n : max;
}, 1000);

function generateId(prefix: string): string {
    nextId += 1;
    return `${prefix}-${nextId}`;
}

function cloneMeeting(meeting: Meeting): Meeting {
    return { ...meeting };
}

export interface MockMeetingIdentityContext {
    currentUser: ProjectMember;
    projectMembers?: ProjectMember[];
}

/** Runtime mock ids are numeric; fixture ids contain descriptive suffixes. */
function isRuntimeMeeting(meeting: Meeting): boolean {
    return /^m-\d+$/.test(meeting.id);
}

/**
 * One-time compatibility migration for meetings created when every invoking
 * Jira user was incorrectly represented by the Jordan Avery fixture.
 */
export function migrateLegacyMockCurrentUser(
    currentUser: ProjectMember,
): boolean {
    if (
        !currentUser.accountId
        || currentUser.accountId === CURRENT_USER.accountId
    )
        return false;

    const migratedMeetingIds: string[] = [];
    let changed = false;
    meetings = meetings.map((meeting) => {
        if (!isRuntimeMeeting(meeting)) return meeting;

        const ownsCreator = meeting.creatorId === CURRENT_USER.accountId;
        const ownsHost = meeting.hostId === CURRENT_USER.accountId;
        if (!ownsCreator && !ownsHost) return meeting;

        changed = true;
        migratedMeetingIds.push(meeting.id);
        return {
            ...meeting,
            creatorId: ownsCreator ? currentUser.accountId : meeting.creatorId,
            creatorName: ownsCreator
                ? currentUser.displayName
                : meeting.creatorName,
            hostId: ownsHost ? currentUser.accountId : meeting.hostId,
            hostName: ownsHost ? currentUser.displayName : meeting.hostName,
        };
    });

    if (changed) {
        persistMeetings(meetings);
        migrateMockParticipantIdentity(
            migratedMeetingIds,
            CURRENT_USER.accountId,
            currentUser,
        );
    }
    return changed;
}

export async function listIssueMeetings(issueKey: string): Promise<Meeting[]> {
    return delay(
        meetings.filter((m) => m.issueKey === issueKey).map(cloneMeeting),
    );
}

export async function listProjectMeetings(
    filters: MeetingListFilters,
): Promise<Meeting[]> {
    const search = filters.search?.trim().toLowerCase();
    const issueKey = filters.issueKey?.trim().toUpperCase();
    const result = meetings.filter((m) => {
        if (m.projectKey !== filters.projectKey) return false;
        if (filters.status && m.status !== filters.status) return false;
        if (
            filters.createdByAccountId
            && m.creatorId !== filters.createdByAccountId
        )
            return false;
        if (issueKey && m.issueKey.toUpperCase() !== issueKey) return false;
        if (search && !m.title.toLowerCase().includes(search)) return false;
        return true;
    });
    return delay(result.map(cloneMeeting));
}

export async function getMeeting(meetingId: string): Promise<Meeting> {
    const meeting = meetings.find((m) => m.id === meetingId);
    if (!meeting) throw new Error(`Mock meeting not found: ${meetingId}`);
    return delay(cloneMeeting(meeting));
}

/** Any RUNNING meeting hosted by the current user, optionally excluding one issue. */
export async function findRunningMeetingHostedByUser(
    accountId: string,
    excludingIssueKey?: string,
): Promise<Meeting | null> {
    const hit = meetings.find(
        (m) =>
            m.status === 'RUNNING'
            && m.hostId === accountId
            && m.issueKey !== excludingIssueKey,
    );
    return delay(hit ? cloneMeeting(hit) : null);
}

export async function createInstantMeeting(
    input: CreateInstantMeetingInput,
    identity: MockMeetingIdentityContext = { currentUser: CURRENT_USER },
): Promise<Meeting> {
    const projectKey = input.issueKey.split('-')[0];
    const actor = identity.currentUser;
    const meeting: Meeting = {
        id: generateId('m'),
        title: input.title,
        description: input.description,
        projectId: `project-${projectKey.toLowerCase()}`,
        projectKey,
        issueId: `issue-${input.issueKey}`,
        issueKey: input.issueKey,
        creatorId: actor.accountId,
        creatorName: actor.displayName,
        hostId: actor.accountId,
        hostName: actor.displayName,
        startedAt: new Date().toISOString(),
        status: 'RUNNING',
        participantCount: 1 + (input.invitees?.length ?? 0),
    };
    meetings = [meeting, ...meetings];
    persistMeetings(meetings);
    setMockParticipants(
        meeting.id,
        actor,
        (input.invitees ?? []).map((invitee) => invitee.accountId),
        identity.projectMembers,
    );
    return delay(cloneMeeting(meeting));
}

export async function scheduleMeeting(
    input: ScheduleMeetingInput,
    identity: MockMeetingIdentityContext = { currentUser: CURRENT_USER },
): Promise<Meeting> {
    const projectKey = input.issueKey.split('-')[0];
    const actor = identity.currentUser;
    const meeting: Meeting = {
        id: generateId('m'),
        title: input.title,
        description: input.description,
        projectId: `project-${projectKey.toLowerCase()}`,
        projectKey,
        issueId: `issue-${input.issueKey}`,
        issueKey: input.issueKey,
        creatorId: actor.accountId,
        creatorName: actor.displayName,
        hostId: actor.accountId,
        hostName: actor.displayName,
        scheduledAt: input.startTime,
        status: 'SCHEDULED',
        participantCount: 1 + input.invitees.length,
    };
    meetings = [meeting, ...meetings];
    persistMeetings(meetings);
    setMockParticipants(
        meeting.id,
        actor,
        input.invitees.map((invitee) => invitee.accountId),
        identity.projectMembers,
    );
    return delay(cloneMeeting(meeting));
}

export async function updateMeeting(
    meetingId: string,
    input: UpdateMeetingInput,
): Promise<Meeting> {
    let updated: Meeting | undefined;
    meetings = meetings.map((m) => {
        if (m.id !== meetingId) return m;
        updated = {
            ...m,
            title: input.title ?? m.title,
            description: input.description ?? m.description,
            scheduledAt: input.startTime ?? m.scheduledAt,
        };
        return updated;
    });
    persistMeetings(meetings);
    if (!updated) throw new Error(`Mock meeting not found: ${meetingId}`);
    return delay(cloneMeeting(updated));
}

export async function cancelMeeting(meetingId: string): Promise<void> {
    meetings = meetings.map((m) =>
        m.id === meetingId ? { ...m, status: 'CANCELED' } : m,
    );
    persistMeetings(meetings);
    return delay(undefined);
}

export async function startMeeting(meetingId: string): Promise<Meeting> {
    let updated: Meeting | undefined;
    meetings = meetings.map((m) => {
        if (m.id !== meetingId) return m;
        updated = {
            ...m,
            status: 'RUNNING',
            startedAt: new Date().toISOString(),
        };
        return updated;
    });
    persistMeetings(meetings);
    if (!updated) throw new Error(`Mock meeting not found: ${meetingId}`);
    return delay(cloneMeeting(updated));
}

export async function endMeeting(meetingId: string): Promise<Meeting> {
    let updated: Meeting | undefined;
    meetings = meetings.map((meeting) => {
        if (meeting.id !== meetingId) return meeting;
        updated = {
            ...meeting,
            status: 'COMPLETED',
            endedAt: new Date().toISOString(),
        };
        return updated;
    });
    persistMeetings(meetings);
    if (!updated) throw new Error(`Mock meeting not found: ${meetingId}`);
    return delay(cloneMeeting(updated));
}

export async function listMeetingParticipants(
    meetingId: string,
): Promise<Participant[]> {
    return delay(getMockParticipants(meetingId));
}

export async function getMeetingRecording(
    meetingId: string,
): Promise<Recording | null> {
    return delay(recordings[meetingId] ?? getMockRecording(meetingId));
}

export async function startRecording(meetingId: string): Promise<Recording> {
    const recording: Recording = {
        id: recordings[meetingId]?.id ?? generateId('r'),
        meetingId,
        status: 'RECORDING',
        startedAt: new Date().toISOString(),
    };
    recordings[meetingId] = recording;
    return delay({ ...recording });
}

export async function stopRecording(meetingId: string): Promise<Recording> {
    const existing = recordings[meetingId];
    const recording: Recording = {
        id: existing?.id ?? generateId('r'),
        meetingId,
        status: 'COMPLETED',
        startedAt: existing?.startedAt,
        endedAt: new Date().toISOString(),
        fileUrl: 'https://example.invalid/recordings/mock.mp4',
    };
    recordings[meetingId] = recording;
    return delay({ ...recording });
}
