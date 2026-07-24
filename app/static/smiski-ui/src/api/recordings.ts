/**
 * Recordings API — typed client signatures for the `record` service via the backend gateway
 * once recording leaves prototype/mock mode.
 */
import type { Recording } from '../domain';
import { apiRequest } from './client';
import { recordingEndpoints } from './endpoints';
import { recordingFromBackend } from './mappers';

/** Fetch the recording (if any) attached to a meeting. */
export async function getMeetingRecording(
    meetingId: string,
): Promise<Recording | null> {
    const payload = await apiRequest<unknown>(
        recordingEndpoints.byMeeting(meetingId),
    );
    return recordingFromBackend(payload);
}

/** Start recording a running meeting (requires EDIT_MEETING). */
export async function startRecording(meetingId: string): Promise<Recording> {
    const payload = await apiRequest<unknown>(
        recordingEndpoints.start(meetingId),
        {
            method: 'POST',
        },
    );
    const recording = recordingFromBackend(payload);
    if (!recording) throw new Error('Backend did not return a recording.');
    return recording;
}

/** Stop an in-progress recording (requires EDIT_MEETING). */
export async function stopRecording(meetingId: string): Promise<Recording> {
    const payload = await apiRequest<unknown>(
        recordingEndpoints.stop(meetingId),
        {
            method: 'POST',
        },
    );
    const recording = recordingFromBackend(payload);
    if (!recording) throw new Error('Backend did not return a recording.');
    return recording;
}
