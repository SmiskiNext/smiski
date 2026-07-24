/**
 * Participant — a Jira user's presence within a meeting.
 *
 * `accountId` / `displayName` originate from the Jira user identity (resolved by
 * the backend user-management service). Fields only.
 */
import type { ParticipantRole } from './enums';

export interface Participant {
    accountId: string;
    displayName: string;
    role: ParticipantRole;
    /** ISO datetime the participant joined the room. */
    joinedAt?: string;
    /** ISO datetime the participant left the room. */
    leftAt?: string;
}
