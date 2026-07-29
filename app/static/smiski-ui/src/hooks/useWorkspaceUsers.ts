/**
 * useWorkspaceUsers — Jira site (workspace) users for the invite picker, with a
 * server-side typeahead.
 *
 * Data source switches by environment, same pattern as `useProjectIssues` /
 * `useProjectMembers`: standalone `vite dev` has no Forge bridge, so it reads
 * the mock directory; a real Forge context invokes the `searchWorkspaceUsers`
 * resolver. The search term is debounced (250ms, mirroring `IssuePicker`) so
 * each keystroke does not hit Jira, and results are cached per debounced term.
 */
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import {
    searchWorkspaceUsers,
    type WorkspaceUser,
} from '../api/workspaceUsers';
import { searchMockWorkspaceUsers } from '../mocks/workspaceUsers';
import { queryKeys } from './queryKeys';

const DEBOUNCE_MS = 250;

export interface UseWorkspaceUsersResult {
    users: WorkspaceUser[];
    loading: boolean;
    error: Error | null;
}

export function useWorkspaceUsers(
    query: string,
    enabled = true,
): UseWorkspaceUsersResult {
    const [debouncedQuery, setDebouncedQuery] = useState(query);

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedQuery(query), DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [query]);

    const result = useQuery({
        queryKey: queryKeys.workspaceUsers(debouncedQuery),
        queryFn: () =>
            import.meta.env.DEV
                ? searchMockWorkspaceUsers(debouncedQuery)
                : searchWorkspaceUsers(debouncedQuery),
        enabled,
        staleTime: 30_000,
    });

    return {
        users: result.data ?? [],
        loading: result.isLoading || result.isFetching,
        error: result.error as Error | null,
    };
}
