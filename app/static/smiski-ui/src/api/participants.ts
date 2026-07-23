/**
 * Participants API — typed client signatures via the backend gateway.
 */
import type { Participant } from '../domain';
import { apiRequest } from './client';
import { meetingEndpoints } from './endpoints';
import { participantsFromBackend } from './mappers';

/** List participants for a meeting (live + historical). */
export async function getMeetingParticipants(meetingId: string): Promise<Participant[]> {
  const payload = await apiRequest<unknown>(meetingEndpoints.participants(meetingId));
  return participantsFromBackend(payload);
}
