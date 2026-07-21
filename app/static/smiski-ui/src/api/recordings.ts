/**
 * Recordings API — typed client signatures for the `record` service via Kong
 * (STUB). Recording is out-of-scope for the initial module (DOCS1.md); these
 * signatures exist for the project-page placeholder controls. No fetch logic.
 */
import type { Recording } from '../domain';

/** Fetch the recording (if any) attached to a meeting. */
export async function getMeetingRecording(_meetingId: string): Promise<Recording | null> {
  throw new Error('Not implemented: getMeetingRecording');
}

/** Start recording a running meeting (requires EDIT_MEETING). */
export async function startRecording(_meetingId: string): Promise<Recording> {
  throw new Error('Not implemented: startRecording');
}

/** Stop an in-progress recording (requires EDIT_MEETING). */
export async function stopRecording(_meetingId: string): Promise<Recording> {
  throw new Error('Not implemented: stopRecording');
}
