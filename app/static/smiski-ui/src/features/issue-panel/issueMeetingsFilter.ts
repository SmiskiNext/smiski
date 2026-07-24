/**
 * Pure filter/sort policy for the Issue Panel's unified meeting list — kept
 * separate from IssueMeetingsPanel.tsx so it's independently testable and so
 * the component only owns rendering, not list-ordering rules.
 */
import type { Meeting, MeetingStatus } from '../../domain';

export interface IssueMeetingsFilterValue {
    search?: string;
    status?: MeetingStatus;
}

/** Running first, then soonest-scheduled, then most-recently-finished. */
const STATUS_PRIORITY: Record<MeetingStatus, number> = {
    RUNNING: 0,
    SCHEDULED: 1,
    COMPLETED: 2,
    CANCELED: 3,
};

function meetingTimestamp(meeting: Meeting): number {
    const value = meeting.startedAt ?? meeting.scheduledAt ?? meeting.endedAt;
    return value ? new Date(value).getTime() : 0;
}

export function filterAndSortIssueMeetings(
    meetings: Meeting[],
    filter: IssueMeetingsFilterValue,
): Meeting[] {
    const search = filter.search?.trim().toLowerCase();

    return meetings
        .filter((meeting) => !filter.status || meeting.status === filter.status)
        .filter(
            (meeting) =>
                !search || meeting.title.toLowerCase().includes(search),
        )
        .sort((a, b) => {
            const statusDiff =
                STATUS_PRIORITY[a.status] - STATUS_PRIORITY[b.status];
            if (statusDiff !== 0) return statusDiff;
            // Scheduled meetings read soonest-first; everything else, most-recent-first.
            const timeDiff = meetingTimestamp(b) - meetingTimestamp(a);
            return a.status === 'SCHEDULED' ? -timeDiff : timeDiff;
        });
}
