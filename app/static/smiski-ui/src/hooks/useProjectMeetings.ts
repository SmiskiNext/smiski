/**
 * useProjectMeetings — meetings across a project for the dashboard table.
 *
 * Still backed by the in-memory mock: the backend `meet` `list` operation
 * only supports an exact `issueKey` filter (plus creator/status/search), not
 * a project-wide one, so there is no contract-correct way to wire this to
 * the real API yet without either scanning every issue in the project
 * (wrong/expensive) or a backend change (out of scope here).
 */
import { useQuery } from '@tanstack/react-query';
import type { MeetingListFilters } from '../api/meetings';
import type { Meeting } from '../domain';
import { listProjectMeetings } from '../mocks/db';
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
