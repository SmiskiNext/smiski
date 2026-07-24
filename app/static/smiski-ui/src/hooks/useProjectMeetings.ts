/**
 * useProjectMeetings — meetings across a project for the dashboard table.
 *
 * Backed by React Query over the mock in-memory db. Swap point for real
 * wiring: replace the `queryFn` below with `api.getProjectMeetings(filters)`
 * (same signature) once the Kong Gateway integration exists.
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
        // TODO: swap for api.getProjectMeetings(filters) once the backend is wired up.
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
