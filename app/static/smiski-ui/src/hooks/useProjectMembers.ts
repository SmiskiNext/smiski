/**
 * useProjectMembers — real Jira project members, used to populate the
 * meeting-participant picker (Schedule Meeting, Start Instant Meeting) and
 * the dashboard's "creator" filter.
 *
 * Members always come from Jira directly via `requestJira`, so a Forge context
 * is required.
 */
import { useQuery } from '@tanstack/react-query';
import { getProjectMembers } from '../api/projectMembers';
import type { ProjectMember } from '../domain';
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
        queryFn: () => getProjectMembers(projectKey as string),
        enabled: Boolean(projectKey),
        staleTime: 30_000,
    });

    return {
        members: result.data ?? [],
        loading: result.isLoading,
        error: result.error as Error | null,
    };
}
