/**
 * useProjectIssues — real Jira issues for the current project, used to bind a
 * meeting to an issue when creating/scheduling one.
 *
 * Data source switches by environment: standalone `vite dev` has no Forge
 * bridge, so it reads the mock issue list; a real Forge context (tunnel or
 * deployed) calls Jira directly via `requestJira`. Both share the same
 * signature, so this is the only place that knows the difference.
 */
import { useQuery } from '@tanstack/react-query';
import type { JiraIssue } from '../domain';
import { getProjectIssues } from '../api/issues';
import { listProjectIssues } from '../mocks/issues';
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
    queryFn: () =>
      import.meta.env.DEV
        ? listProjectIssues(projectKey, query)
        : getProjectIssues(projectKey, query),
    enabled: enabled && Boolean(projectKey),
    staleTime: 30_000,
  });

  return {
    issues: result.data ?? [],
    loading: result.isLoading,
    error: result.error as Error | null,
  };
}
