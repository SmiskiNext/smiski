'use client';

import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { getMe, listParticipatedMeetings } from '@/generated/sdk.gen.ts';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';

const PAGE_SIZE = 20;
const HISTORY_STATUS_FILTER = 'ENDED,CANCELLED';
const SESSION_EXPIRED_CODES = new Set([
    'UNAUTHORIZED',
    'AUTHENTICATION_REQUIRED',
    'INVALID_TOKEN',
    'TOKEN_EXPIRED',
]);

export type MeetingHistoryState =
    | { phase: 'LOADING' }
    | { phase: 'EMPTY' }
    | { phase: 'ERROR'; message: string }
    | {
          phase: 'SUCCESS';
          meetings: MeetingManagementMeetingResponse[];
          nextPageToken: string | null;
          isRefreshing: boolean;
          isLoadingMore: boolean;
      };

type FetchHistoryPageOptions = {
    userId: string;
    pageToken?: string;
};

type UseMeetingHistoryResult = {
    state: MeetingHistoryState;
    retry: () => void;
    refresh: () => void;
    loadMore: () => void;
};

function normalizePageToken(
    pageToken: string | null | undefined,
): string | null {
    if (!pageToken) return null;
    const trimmed = pageToken.trim();
    return trimmed.length > 0 ? trimmed : null;
}

function isSessionExpiredError(error: unknown): boolean {
    return (
        error instanceof ApiFailError && SESSION_EXPIRED_CODES.has(error.code)
    );
}

async function fetchHistoryPage({
    userId,
    pageToken,
}: FetchHistoryPageOptions) {
    const { data } = await listParticipatedMeetings({
        path: { userId },
        query: {
            pageSize: PAGE_SIZE,
            pageToken,
            status: HISTORY_STATUS_FILTER,
        },
        throwOnError: true,
    });

    return {
        meetings: data?.content ?? [],
        nextPageToken: normalizePageToken(data?.nextPageToken),
    };
}

export function useMeetingHistory(): UseMeetingHistoryResult {
    const t = useTranslations('workspace.history');
    const [state, setState] = useState<MeetingHistoryState>({
        phase: 'LOADING',
    });
    const userIdRef = useRef<string | null>(null);
    const inFlightRef = useRef(false);
    const loadMoreRef = useRef<() => void>(() => undefined);

    const resolveUserId = useCallback(async () => {
        if (userIdRef.current) return userIdRef.current;

        const { data } = await getMe({ throwOnError: true });
        const userId = data?.id;
        if (!userId) {
            throw new ApiFailError('UNAUTHORIZED', t('errorDescription'));
        }
        userIdRef.current = userId;
        return userId;
    }, [t]);

    const getErrorMessage = useCallback(
        (error: unknown) => {
            if (isSessionExpiredError(error)) {
                return t('sessionExpired');
            }
            if (error instanceof ApiError || error instanceof ApiFailError) {
                return error.message;
            }
            return t('errorDescription');
        },
        [t],
    );

    const loadInitial = useCallback(async () => {
        if (inFlightRef.current) return;
        inFlightRef.current = true;
        setState({ phase: 'LOADING' });

        try {
            const userId = await resolveUserId();
            const page = await fetchHistoryPage({ userId });
            setState(
                page.meetings.length === 0
                    ? { phase: 'EMPTY' }
                    : {
                          phase: 'SUCCESS',
                          meetings: page.meetings,
                          nextPageToken: page.nextPageToken,
                          isRefreshing: false,
                          isLoadingMore: false,
                      },
            );
        } catch (error) {
            setState({ phase: 'ERROR', message: getErrorMessage(error) });
        } finally {
            inFlightRef.current = false;
        }
    }, [resolveUserId, getErrorMessage]);

    useEffect(() => {
        void loadInitial();
    }, [loadInitial]);

    const retry = useCallback(() => {
        void loadInitial();
    }, [loadInitial]);

    const refresh = useCallback(() => {
        setState((current) => {
            if (current.phase === 'SUCCESS') {
                void (async (
                    previous: Extract<
                        MeetingHistoryState,
                        { phase: 'SUCCESS' }
                    >,
                ) => {
                    try {
                        const userId = await resolveUserId();
                        const page = await fetchHistoryPage({ userId });
                        setState(
                            page.meetings.length === 0
                                ? { phase: 'EMPTY' }
                                : {
                                      phase: 'SUCCESS',
                                      meetings: page.meetings,
                                      nextPageToken: page.nextPageToken,
                                      isRefreshing: false,
                                      isLoadingMore: false,
                                  },
                        );
                    } catch {
                        setState(previous);
                        toast.error(t('refreshErrorTitle'), {
                            description: t('refreshErrorDescription'),
                        });
                    }
                })({ ...current, isRefreshing: false });

                return { ...current, isRefreshing: true };
            }

            void loadInitial();
            return current;
        });
    }, [resolveUserId, loadInitial, t]);

    const loadMore = useCallback(() => {
        setState((current) => {
            if (
                current.phase !== 'SUCCESS'
                || current.isLoadingMore
                || current.isRefreshing
                || !current.nextPageToken
            ) {
                return current;
            }

            const pageToken = current.nextPageToken;

            void (async () => {
                try {
                    const userId = await resolveUserId();
                    const page = await fetchHistoryPage({ userId, pageToken });
                    setState((latest) => {
                        if (latest.phase !== 'SUCCESS') return latest;
                        return {
                            phase: 'SUCCESS',
                            meetings: [...latest.meetings, ...page.meetings],
                            nextPageToken: page.nextPageToken,
                            isRefreshing: false,
                            isLoadingMore: false,
                        };
                    });
                } catch {
                    setState((latest) => {
                        if (latest.phase !== 'SUCCESS') return latest;
                        return { ...latest, isLoadingMore: false };
                    });
                    toast.error(t('loadMoreErrorTitle'), {
                        description: t('loadMoreErrorDescription'),
                        action: {
                            label: t('loadMoreRetry'),
                            onClick: () => loadMoreRef.current(),
                        },
                    });
                }
            })();

            return { ...current, isLoadingMore: true };
        });
    }, [resolveUserId, t]);

    loadMoreRef.current = loadMore;

    return { state, retry, refresh, loadMore };
}
