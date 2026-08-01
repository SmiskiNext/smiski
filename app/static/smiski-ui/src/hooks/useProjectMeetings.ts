/**
 * useProjectMeetings — meetings across a project for the dashboard table.
 *
 * Backed by the `getProjectMeetings` resolver function, which is an
 * unimplemented stub — the real `meet` backend's `list` operation has no
 * project-wide filter yet (only an exact `issueKey` filter), see
 * `app/src/index.ts`.
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
