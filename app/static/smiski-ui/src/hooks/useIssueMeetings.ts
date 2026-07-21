/**
 * useIssueMeetings — meetings bound to the current Issue (UC02/UC07).
 *
 * Backed by React Query over the mock in-memory db. Swap point for real
 * wiring: replace the `queryFn` below with `api.getIssueMeetings(issueKey)`
 * (same signature) once the Kong Gateway integration exists.
 */
import { useQuery } from '@tanstack/react-query';
import type { Meeting } from '../domain';
import { listIssueMeetings } from '../mocks/db';
import { queryKeys } from './queryKeys';

export interface UseIssueMeetingsResult {
  meetings: Meeting[];
  loading: boolean;
  error: Error | null;
  refresh: () => void;
}

export function useIssueMeetings(issueKey?: string): UseIssueMeetingsResult {
  const query = useQuery({
    queryKey: issueKey ? queryKeys.issueMeetings(issueKey) : ['meetings', 'issue', 'none'],
    // TODO: swap for api.getIssueMeetings(issueKey) once the backend is wired up.
    queryFn: () => listIssueMeetings(issueKey as string),
    enabled: Boolean(issueKey),
  });

  return {
    meetings: query.data ?? [],
    loading: query.isLoading,
    error: query.error as Error | null,
    refresh: () => query.refetch(),
  };
}
