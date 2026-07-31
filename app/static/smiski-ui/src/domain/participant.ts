/**
 * Participant — a Jira user's presence within a meeting.
 *
 * `accountId` / `displayName` originate from the Jira user identity (resolved by
 * the backend user-management service). Fields only.
 */
import type { ParticipantRole } from './enums';
import type { ProjectMember } from './projectMember';

export interface Participant {
    accountId: string;
    displayName: string;
    role: ParticipantRole;
    /** ISO datetime the participant joined the room. */
    joinedAt?: string;
    /** ISO datetime the participant left the room. */
    leftAt?: string;
}

/**
 * Resolve a stored participant's `displayName` against the latest Jira
 * directory (current user + project members), so a name change since the
 * participant joined shows up without needing to re-persist anything.
 */
export function resolveParticipantDisplayNames(
    participants: Participant[],
    currentUser: ProjectMember,
    projectMembers: ProjectMember[],
): Participant[] {
    const memberByAccountId = new Map(
        [currentUser, ...projectMembers].map(
            (member) => [member.accountId, member] as const,
        ),
    );

    return participants.map((value) => {
        const member = memberByAccountId.get(value.accountId);
        return member && member.displayName !== value.displayName
            ? { ...value, displayName: member.displayName }
            : value;
    });
}
