/**
 * useMeeting — a single meeting's full detail, via the backend `get`
 * operation (`api/meetings.ts`'s `getMeeting`).
 */
import { useQuery } from '@tanstack/react-query';
import { getMeeting } from '../api/meetings';
import { queryKeys } from './queryKeys';

export function useMeeting(meetingId?: string) {
    const query = useQuery({
        queryKey: meetingId
            ? queryKeys.meeting(meetingId)
            : ['meeting', 'none'],
        queryFn: () => getMeeting(meetingId as string),
        enabled: Boolean(meetingId),
        select: (data) => data.meeting,
    });

    return {
        meeting: query.data ?? null,
        loading: query.isLoading,
        error: query.error as Error | null,
    };
}
