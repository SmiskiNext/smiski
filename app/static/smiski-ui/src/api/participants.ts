/**
 * Participants API — typed client signatures via Kong (STUB). No fetch logic.
 */
import type { Participant } from '../domain';

/** List participants for a meeting (live + historical). */
export async function getMeetingParticipants(_meetingId: string): Promise<Participant[]> {
  throw new Error('Not implemented: getMeetingParticipants');
}
