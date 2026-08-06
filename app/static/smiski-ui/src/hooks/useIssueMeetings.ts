/**
 * Offset-paginated meetings linked to one Jira issue. Pages are accumulated
 * for the compact Issue Panel and exposed through an explicit load-more API.
 */
import { useInfiniteQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { DEFAULT_MEETING_PAGE_SIZE, listIssueMeetings } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { Meeting } from '../domain';
import { resolveMeetingHostNames } from '../domain';
import { queryKeys } from './queryKeys';
import { useProjectMembers } from './useProjectMembers';

export interface UseIssueMeetingsResult {
    meetings: Meeting[];
    total: number;
    loading: boolean;
    loadingMore: boolean;
    hasMore: boolean;
    error: Error | null;
    loadMoreError: Error | null;
    loadMore: () => void;
    refresh: () => void;
}

export function useIssueMeetings(
    issueId?: string,
    projectKey?: string,
    enabled = true,
): UseIssueMeetingsResult {
    const currentUser = useCurrentUser();
    const projectMembers = useProjectMembers(projectKey);
    const query = useInfiniteQuery({
        queryKey: issueId
            ? queryKeys.issueMeetings(issueId)
            : ['meetings', 'issue', 'none'],
        queryFn: ({ pageParam }) =>
            listIssueMeetings(issueId as string, {
                offset: pageParam,
                pageSize: DEFAULT_MEETING_PAGE_SIZE,
            }),
        initialPageParam: 0,
        getNextPageParam: (lastPage) =>
            lastPage.hasNext
                ? lastPage.offset + lastPage.meetings.length
                : undefined,
        enabled: Boolean(issueId) && enabled,
    });

    const pages = query.data?.pages ?? [];
    const meetings = useMemo(
        () =>
            resolveMeetingHostNames(
                pages.flatMap((page) => page.meetings),
                [currentUser, ...projectMembers.members],
            ),
        [pages, currentUser, projectMembers.members],
    );

    return {
        meetings,
        total: pages[0]?.total ?? meetings.length,
        loading: query.isLoading,
        loadingMore: query.isFetchingNextPage,
        hasMore: query.hasNextPage,
        error: pages.length === 0 ? (query.error as Error | null) : null,
        loadMoreError: pages.length > 0 ? (query.error as Error | null) : null,
        loadMore: () => {
            if (query.hasNextPage && !query.isFetchingNextPage) {
                void query.fetchNextPage();
            }
        },
        refresh: () => {
            void query.refetch();
        },
    };
}
