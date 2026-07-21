/**
 * useMeetingRecording — the recording (if any) attached to a meeting, plus
 * start/stop mutations. Swap point: replace mock db calls with
 * `api.getMeetingRecording` / `api.startRecording` / `api.stopRecording`.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMeetingRecording, startRecording, stopRecording } from '../mocks/db';
import { queryKeys } from './queryKeys';

export function useMeetingRecording(meetingId?: string) {
  const query = useQuery({
    queryKey: meetingId ? queryKeys.recording(meetingId) : ['recording', 'none'],
    queryFn: () => getMeetingRecording(meetingId as string),
    enabled: Boolean(meetingId),
  });

  return {
    recording: query.data ?? null,
    loading: query.isLoading,
    error: query.error as Error | null,
  };
}

export function useRecordingMutations(meetingId: string) {
  const queryClient = useQueryClient();
  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: queryKeys.recording(meetingId) });

  const start = useMutation({
    mutationFn: () => startRecording(meetingId),
    onSuccess: invalidate,
  });
  const stop = useMutation({
    mutationFn: () => stopRecording(meetingId),
    onSuccess: invalidate,
  });

  return { start, stop };
}
