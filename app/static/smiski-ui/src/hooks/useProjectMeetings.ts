/**
 * useProjectMeetings — meetings across a project for the dashboard table.
 *
 * Defaults to showing only the current user's meetings unless
 * `createdByAccountId` is explicitly provided in filters.
 */
import { useQuery } from '@tanstack/react-query';
import type { MeetingListFilters } from '../api/meetings';
import { listProjectMeetings } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { Meeting } from '../domain';
import { queryKeys } from './queryKeys';

export interface UseProjectMeetingsResult {
    meetings: Meeting[];
    loading: boolean;
    error: Error | null;
    refresh: () => void;
}

export function useProjectMeetings(
    filters: MeetingListFilters,
    enabled = true,
): UseProjectMeetingsResult {
    const currentUser = useCurrentUser();

    const effectiveFilters = {
        ...filters,
        createdByAccountId: filters.createdByAccountId ?? currentUser.accountId,
    };

    const query = useQuery({
        queryKey: queryKeys.projectMeetings(effectiveFilters),
        queryFn: () => listProjectMeetings(effectiveFilters),
        enabled,
    });

    return {
        meetings: query.data ?? [],
        loading: query.isLoading,
        error: query.error as Error | null,
        refresh: () => query.refetch(),
    };
}
