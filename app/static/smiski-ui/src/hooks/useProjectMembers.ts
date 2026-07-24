/**
 * useProjectMembers — real Jira project members, used to populate the
 * meeting-participant picker (Schedule Meeting, Start Instant Meeting) and
 * the dashboard's "creator" filter.
 *
 * Data source switches by environment, same pattern as `useProjectIssues`:
 * standalone `vite dev` has no Forge bridge, so it reads the mock user
 * directory; a real Forge context calls Jira directly via `requestJira`.
 */
import { useQuery } from '@tanstack/react-query';
import { getProjectMembers } from '../api/projectMembers';
import type { ProjectMember } from '../domain';
import { listProjectMembers } from '../mocks/projectMembers';
import { queryKeys } from './queryKeys';

export interface UseProjectMembersResult {
    members: ProjectMember[];
    loading: boolean;
    error: Error | null;
}

export function useProjectMembers(
    projectKey?: string,
): UseProjectMembersResult {
    const result = useQuery({
        queryKey: queryKeys.projectMembers(projectKey ?? ''),
        queryFn: () =>
            import.meta.env.DEV
                ? listProjectMembers(projectKey as string)
                : getProjectMembers(projectKey as string),
        enabled: Boolean(projectKey),
        staleTime: 30_000,
    });

    return {
        members: result.data ?? [],
        loading: result.isLoading,
        error: result.error as Error | null,
    };
}
