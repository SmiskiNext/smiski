/**
 * useIssueMeetings — meetings bound to the current Issue (UC02/UC07), via the
 * backend `list` operation filtered by exact `issueKey`.
 */
import { useQuery } from '@tanstack/react-query';
import { listIssueMeetings } from '../api/meetings';
import type { Meeting } from '../domain';
import { queryKeys } from './queryKeys';

export interface UseIssueMeetingsResult {
    meetings: Meeting[];
    loading: boolean;
    error: Error | null;
    refresh: () => void;
}

export function useIssueMeetings(issueKey?: string): UseIssueMeetingsResult {
    const query = useQuery({
        queryKey: issueKey
            ? queryKeys.issueMeetings(issueKey)
            : ['meetings', 'issue', 'none'],
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
