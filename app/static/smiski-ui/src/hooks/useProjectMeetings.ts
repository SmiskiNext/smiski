/**
 * Cursor-paginated project meeting query. Cursor history is kept client-side
 * so users can move backwards even though the backend only returns a next
 * cursor. Changing any filter starts a fresh cursor chain automatically. The
 * current user's meetings are shown by default unless `createdByAccountId` is
 * explicitly provided.
 */
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import {
    DEFAULT_MEETING_PAGE_SIZE,
    listProjectMeetings,
    type MeetingListFilters,
} from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { Meeting } from '../domain';
import { queryKeys } from './queryKeys';

interface CursorNavigation {
    scope: string;
    tokens: Array<string | undefined>;
    pageIndex: number;
}

export interface UseProjectMeetingsResult {
    meetings: Meeting[];
    loading: boolean;
    error: Error | null;
    pageNumber: number;
    hasPreviousPage: boolean;
    hasNextPage: boolean;
    nextPage: () => void;
    previousPage: () => void;
    resetPagination: () => void;
    refresh: () => void;
}

function paginationScope(filters: MeetingListFilters): string {
    return JSON.stringify([
        filters.projectKey,
        filters.issueKey ?? '',
        filters.createdByAccountId ?? '',
        [...(filters.statuses ?? [])].sort().join(','),
        filters.search ?? '',
        filters.sort ?? '',
    ]);
}

function initialNavigation(scope: string): CursorNavigation {
    return { scope, tokens: [undefined], pageIndex: 0 };
}

export function useProjectMeetings(
    filters: MeetingListFilters,
    enabled = true,
): UseProjectMeetingsResult {
    const currentUser = useCurrentUser();

    const effectiveFilters = {
        ...filters,
        createdByAccountId: filters.createdByAccountId ?? currentUser.accountId,
    };

    const scope = paginationScope(effectiveFilters);
    const [storedNavigation, setNavigation] = useState(() =>
        initialNavigation(scope),
    );
    const navigation =
        storedNavigation.scope === scope
            ? storedNavigation
            : initialNavigation(scope);
    const pageToken = navigation.tokens[navigation.pageIndex];
    const params = {
        ...effectiveFilters,
        pageSize: DEFAULT_MEETING_PAGE_SIZE,
        pageToken,
    };
    const query = useQuery({
        queryKey: queryKeys.projectMeetings(params),
        queryFn: () => listProjectMeetings(params),
        enabled,
    });

    const nextPage = () => {
        const nextPageToken = query.data?.nextPageToken;
        if (!query.data?.hasNext || !nextPageToken) return;
        setNavigation({
            scope,
            tokens: [
                ...navigation.tokens.slice(0, navigation.pageIndex + 1),
                nextPageToken,
            ],
            pageIndex: navigation.pageIndex + 1,
        });
    };

    const previousPage = () => {
        if (navigation.pageIndex === 0) return;
        setNavigation({
            ...navigation,
            pageIndex: navigation.pageIndex - 1,
        });
    };

    return {
        meetings: query.data?.meetings ?? [],
        loading: query.isLoading,
        error: query.error as Error | null,
        pageNumber: navigation.pageIndex + 1,
        hasPreviousPage: navigation.pageIndex > 0,
        hasNextPage: query.data?.hasNext ?? false,
        nextPage,
        previousPage,
        resetPagination: () => setNavigation(initialNavigation(scope)),
        refresh: () => {
            void query.refetch();
        },
    };
}
