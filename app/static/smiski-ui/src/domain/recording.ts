/**
 * Recording — an artifact produced from a meeting.
 *
 * NOTE: recording is listed as out-of-scope in DOCS1.md for the initial Jira
 * module, but the project-page UI carries placeholder recording controls. This
 * type exists so those placeholders are typed; wiring is deferred. Fields only.
 */
import type { RecordingStatus } from './enums';

export interface Recording {
    id: string;
    meetingId: string;
    status: RecordingStatus;
    fileUrl?: string;
    thumbnailUrl?: string;
    title?: string;
    notes?: string;
    durationSeconds?: number;
    /** ISO datetime recording started. */
    startedAt?: string;
    /** ISO datetime recording ended. */
    endedAt?: string;
}
