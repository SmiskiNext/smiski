/**
 * Mock participant rosters, keyed by meetingId. Runtime-added rosters (see
 * `setMockParticipants`) are mirrored to `localStorage` so they survive page
 * reloads, matching `mocks/db.ts`'s meeting persistence.
 */
import type { Participant, ProjectMember } from '../domain';
import { findMockUser } from './users';

const STORAGE_KEY = 'smiski:mock-participants';

function loadPersistedParticipants(): Record<string, Participant[]> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : null;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function persistParticipants(value: Record<string, Participant[]>): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    // Best-effort only (private browsing / storage quota).
  }
}

function participant(
  accountId: string,
  role: Participant['role'],
  joinedMinutesAgo?: number,
  leftMinutesAgo?: number,
  displayName?: string,
): Participant {
  const user = findMockUser(accountId);
  const now = Date.now();
  return {
    accountId,
    displayName: displayName ?? user?.displayName ?? accountId,
    role,
    joinedAt:
      joinedMinutesAgo != null
        ? new Date(now - joinedMinutesAgo * 60 * 1000).toISOString()
        : undefined,
    leftAt:
      leftMinutesAgo != null ? new Date(now - leftMinutesAgo * 60 * 1000).toISOString() : undefined,
  };
}

export let MOCK_PARTICIPANTS: Record<string, Participant[]> = {
  'm-101-running': [
    participant('acc-alex-chen', 'HOST', 12),
    participant('acc-current-jordan', 'PARTICIPANT', 10),
    participant('acc-bao-tran', 'PARTICIPANT', 8),
  ],
  'm-101-completed-1': [
    participant('acc-current-jordan', 'HOST', 26 * 60, 25 * 60),
    participant('acc-chi-nguyen', 'PARTICIPANT', 26 * 60, 25 * 60),
  ],
  'm-102-scheduled-1': [
    participant('acc-current-jordan', 'HOST'),
    participant('acc-sara-kim', 'PARTICIPANT'),
    participant('acc-diego-alvarez', 'PARTICIPANT'),
  ],
  'm-102-scheduled-2': [
    participant('acc-bao-tran', 'HOST'),
    participant('acc-minh-le', 'PARTICIPANT'),
  ],
  'm-102-scheduled-3': [participant('acc-current-jordan', 'HOST')],
  'm-102-completed-1': [
    participant('acc-chi-nguyen', 'HOST', 96 * 60, 95 * 60),
    participant('acc-current-jordan', 'PARTICIPANT', 96 * 60, 95 * 60),
  ],
  'm-55-running-host-conflict': [
    participant('acc-current-jordan', 'HOST', 5),
    participant('acc-minh-le', 'PARTICIPANT', 4),
    participant('acc-sara-kim', 'PARTICIPANT', 3),
  ],
  'm-201-completed-recorded': [
    participant('acc-minh-le', 'HOST', 50 * 60, 49 * 60),
    participant('acc-alex-chen', 'PARTICIPANT', 50 * 60, 49 * 60),
    participant('acc-current-jordan', 'PARTICIPANT', 50 * 60, 49 * 60),
  ],
  'm-202-completed-failed-recording': [
    participant('acc-diego-alvarez', 'HOST', 8 * 60, 7 * 60),
    participant('acc-bao-tran', 'PARTICIPANT', 8 * 60, 7 * 60),
  ],
  ...loadPersistedParticipants(),
};

export function getMockParticipants(meetingId: string): Participant[] {
  return MOCK_PARTICIPANTS[meetingId] ?? [];
}

/** Registers the host + invited participants for a meeting created at runtime. */
export function setMockParticipants(
  meetingId: string,
  host: ProjectMember,
  participantAccountIds: string[] = [],
  projectMembers: ProjectMember[] = [],
): void {
  const memberByAccountId = new Map(
    [host, ...projectMembers].map((member) => [member.accountId, member] as const),
  );

  MOCK_PARTICIPANTS = {
    ...MOCK_PARTICIPANTS,
    [meetingId]: [
      participant(host.accountId, 'HOST', undefined, undefined, host.displayName),
      ...participantAccountIds
        .filter((accountId) => accountId !== host.accountId)
        .map((accountId) =>
          participant(
            accountId,
            'PARTICIPANT',
            undefined,
            undefined,
            memberByAccountId.get(accountId)?.displayName,
          ),
        ),
    ],
  };
  persistParticipants(MOCK_PARTICIPANTS);
}

/**
 * Updates the host entry of meetings created before Jira identity resolution
 * was implemented. The caller deliberately supplies only runtime meeting ids,
 * so seeded fixtures that genuinely belong to Jordan Avery remain untouched.
 */
export function migrateMockParticipantIdentity(
  meetingIds: string[],
  previousAccountId: string,
  currentUser: ProjectMember,
): boolean {
  let changed = false;
  const meetingIdSet = new Set(meetingIds);

  MOCK_PARTICIPANTS = Object.fromEntries(
    Object.entries(MOCK_PARTICIPANTS).map(([meetingId, participants]) => {
      if (!meetingIdSet.has(meetingId)) return [meetingId, participants];

      const migrated = participants.map((value) => {
        if (value.accountId !== previousAccountId) return value;
        changed = true;
        return {
          ...value,
          accountId: currentUser.accountId,
          displayName: currentUser.displayName,
        };
      });
      return [meetingId, migrated];
    }),
  );

  if (changed) persistParticipants(MOCK_PARTICIPANTS);
  return changed;
}

/** Resolve persisted account-id fallbacks against the latest Jira directory. */
export function resolveParticipantDisplayNames(
  participants: Participant[],
  currentUser: ProjectMember,
  projectMembers: ProjectMember[],
): Participant[] {
  const memberByAccountId = new Map(
    [currentUser, ...projectMembers].map((member) => [member.accountId, member] as const),
  );

  return participants.map((value) => {
    const member = memberByAccountId.get(value.accountId);
    return member && member.displayName !== value.displayName
      ? { ...value, displayName: member.displayName }
      : value;
  });
}
