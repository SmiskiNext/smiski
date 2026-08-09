/**
 * useMeeting — a single meeting's full detail, via the backend `get`
 * operation (`api/meetings.ts`'s `getMeeting`).
 */
import { type Query, useQuery } from '@tanstack/react-query';
import { getMeeting, type MeetingDetail } from '../api/meetings';
import { queryKeys } from './queryKeys';

export interface UseMeetingOptions {
    /**
     * Refetch interval applied only while the meeting is still `SCHEDULED`,
     * for callers waiting on the host to start it (see the meeting room's
     * waiting-to-start state). Polling stops on its own once the meeting
     * starts or reaches a terminal status. Omit it to never poll.
     */
    pollWhileScheduledMs?: number;
}

/**
 * Builds the `refetchInterval` that polls only while the meeting has yet to
 * start. It reads the raw fetched `MeetingDetail` — `select` narrows the
 * hook's result to `data.meeting`, not the cached query state.
 */
function pollUntilStarted(intervalMs: number) {
    return (query: Query<MeetingDetail>) =>
        query.state.data?.meeting.status === 'SCHEDULED' ? intervalMs : false;
}

export function useMeeting(
    meetingId?: string,
    options: UseMeetingOptions = {},
) {
    const { pollWhileScheduledMs } = options;
    const query = useQuery({
        queryKey: meetingId
            ? queryKeys.meeting(meetingId)
            : ['meeting', 'none'],
        queryFn: () => getMeeting(meetingId as string),
        enabled: Boolean(meetingId),
        refetchInterval: pollWhileScheduledMs
            ? pollUntilStarted(pollWhileScheduledMs)
            : undefined,
        select: (data) => data.meeting,
    });

    return {
        meeting: query.data ?? null,
        loading: query.isLoading,
        error: query.error as Error | null,
        refetch: query.refetch,
    };
}
