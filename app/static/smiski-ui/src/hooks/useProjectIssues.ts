/**
 * useProjectIssues — real Jira issues for the current project, used to bind a
 * meeting to an issue when creating/scheduling one.
 *
 * Issues always come from Jira directly via `requestJira`, so a Forge context
 * (tunnel or deployed) is required.
 */
import { useQuery } from '@tanstack/react-query';
import { getProjectIssues } from '../api/issues';
import type { JiraIssue } from '../domain';
import { queryKeys } from './queryKeys';

export interface UseProjectIssuesResult {
    issues: JiraIssue[];
    loading: boolean;
    error: Error | null;
}

export function useProjectIssues(
    projectKey: string,
    query?: string,
    enabled = true,
): UseProjectIssuesResult {
    const result = useQuery({
        queryKey: queryKeys.projectIssues(projectKey, query),
        queryFn: () => getProjectIssues(projectKey, query),
        enabled: enabled && Boolean(projectKey),
        staleTime: 30_000,
    });

    return {
        issues: result.data ?? [],
        loading: result.isLoading,
        error: result.error as Error | null,
    };
}
