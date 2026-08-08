/**
 * useWorkspaceUsers — Jira site (workspace) users for the invite picker, with a
 * server-side typeahead.
 *
 * Users always come from Jira directly via
 * `api/workspaceUsers.searchWorkspaceUsers`, so a Forge context is required.
 * The search term is debounced (250ms, mirroring `IssuePicker`) so each
 * keystroke does not hit Jira, and results are cached per debounced term.
 */
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import {
    searchWorkspaceUsers,
    type WorkspaceUser,
} from '../api/workspaceUsers';
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
        queryFn: () => searchWorkspaceUsers(debouncedQuery),
        enabled,
        staleTime: 30_000,
    });

    return {
        users: result.data ?? [],
        loading: result.isLoading || result.isFetching,
        error: result.error as Error | null,
    };
}
