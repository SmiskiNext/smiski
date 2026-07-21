/**
 * useMeeting — a single meeting by id. Swap point: replace `queryFn` with
 * `api.getMeeting(meetingId)`.
 */
import { useQuery } from '@tanstack/react-query';
import { getMeeting } from '../mocks/db';
import { queryKeys } from './queryKeys';

export function useMeeting(meetingId?: string) {
  const query = useQuery({
    queryKey: meetingId ? queryKeys.meeting(meetingId) : ['meeting', 'none'],
    queryFn: () => getMeeting(meetingId as string),
    enabled: Boolean(meetingId),
  });

  return {
    meeting: query.data ?? null,
    loading: query.isLoading,
    error: query.error as Error | null,
  };
}
