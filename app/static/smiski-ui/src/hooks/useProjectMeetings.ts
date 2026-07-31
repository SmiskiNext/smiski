/**
 * useProjectMeetings — meetings across a project for the dashboard table.
 *
 * Demo branch: backed by the Forge-KVS meeting store (`getProjectMeetings`
 * resolver, see `app/src/meetingStore.ts`), which supports a project-wide
 * filter directly — unlike the real `meet` backend's `list` operation, which
 * only supports an exact `issueKey` filter.
 */
import { useQuery } from '@tanstack/react-query';
import type { MeetingListFilters } from '../api/meetings';
import { listProjectMeetings } from '../api/meetings';
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
    const query = useQuery({
        queryKey: queryKeys.projectMeetings(filters),
        queryFn: () => listProjectMeetings(filters),
        enabled,
    });

    return {
        meetings: query.data ?? [],
        loading: query.isLoading,
        error: query.error as Error | null,
        refresh: () => query.refetch(),
    };
}
