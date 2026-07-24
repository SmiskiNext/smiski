/**
 * Mock recordings, keyed by meetingId. Recording is out-of-scope for the
 * initial module (DOCS1.md); these fixtures exist so the project-page
 * placeholder controls have every RecordingStatus to demo.
 */
import type { Recording } from '../domain';

const now = Date.now();
const HOUR = 60 * 60 * 1000;

export const MOCK_RECORDINGS: Record<string, Recording> = {
    'm-201-completed-recorded': {
        id: 'r-201',
        meetingId: 'm-201-completed-recorded',
        status: 'COMPLETED',
        fileUrl: 'https://example.invalid/recordings/r-201.mp4',
        thumbnailUrl: undefined,
        title: 'Architecture review — auth bridge spike',
        durationSeconds: 3180,
        startedAt: new Date(now - 50 * HOUR).toISOString(),
        endedAt: new Date(now - 49 * HOUR).toISOString(),
    },
    'm-202-completed-failed-recording': {
        id: 'r-202',
        meetingId: 'm-202-completed-failed-recording',
        status: 'FAILED',
        notes: 'Recording pipeline timed out — storage upload failed.',
        startedAt: new Date(now - 8 * HOUR).toISOString(),
    },
    'm-101-completed-1': {
        id: 'r-101',
        meetingId: 'm-101-completed-1',
        status: 'PENDING',
    },
};

export function getMockRecording(meetingId: string): Recording | null {
    return MOCK_RECORDINGS[meetingId] ?? null;
}
