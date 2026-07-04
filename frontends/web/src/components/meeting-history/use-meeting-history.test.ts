import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMe, listParticipatedMeetings } from '@/generated/sdk.gen.ts';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import { useMeetingHistory } from './use-meeting-history.ts';

const { toastError, translate } = vi.hoisted(() => {
    const TRANSLATIONS: Record<string, string> = {
        errorDescription:
            'Something went wrong while loading your past meetings. Please try again.',
        sessionExpired: 'Please sign in again.',
        loadMoreErrorTitle: 'Could not load more meetings',
        loadMoreErrorDescription:
            'Your current list is still available. Try loading the next page again.',
        loadMoreRetry: 'Retry',
        refreshErrorTitle: 'Could not refresh meeting history',
        refreshErrorDescription:
            'Your current list is still shown. Try refreshing again in a moment.',
    };
    return {
        toastError: vi.fn(),
        translate: (key: string) =>
            key in TRANSLATIONS ? TRANSLATIONS[key] : key,
    };
});

vi.mock('next-intl', () => ({
    useTranslations: () => translate,
}));

vi.mock('sonner', () => ({
    toast: {
        error: toastError,
    },
}));

vi.mock('@/generated/sdk.gen.ts', () => ({
    getMe: vi.fn(),
    listParticipatedMeetings: vi.fn(),
}));

const mockedGetMe = vi.mocked(getMe);
const mockedListParticipatedMeetings = vi.mocked(listParticipatedMeetings);

function buildMeeting(
    overrides: Partial<MeetingManagementMeetingResponse> = {},
): MeetingManagementMeetingResponse {
    return {
        id: 'meeting-1',
        hostId: 'host-1',
        shortCode: 'ABC1234567',
        title: 'Sprint Review',
        startTime: '2026-06-01T10:00:00Z',
        endTime: '2026-06-01T11:00:00Z',
        type: 'SCHEDULED',
        status: 'ENDED',
        ...overrides,
    };
}

function deferred<T>() {
    let resolve!: (value: T | PromiseLike<T>) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((promiseResolve, promiseReject) => {
        resolve = promiseResolve;
        reject = promiseReject;
    });
    return { promise, resolve, reject };
}

function mockGetMeSuccess() {
    mockedGetMe.mockResolvedValue({
        data: { id: 'user-1' },
    } as Awaited<ReturnType<typeof getMe>>);
}

function mockListResponse(
    meetings: MeetingManagementMeetingResponse[],
    nextPageToken: string | null = null,
) {
    return {
        data: {
            content: meetings,
            nextPageToken,
            pageSize: 20,
        },
    } as Awaited<ReturnType<typeof listParticipatedMeetings>>;
}

describe('useMeetingHistory', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockGetMeSuccess();
    });

    it('populates SUCCESS with the first page on initial load success', async () => {
        const meetings = [buildMeeting()];
        mockedListParticipatedMeetings.mockResolvedValueOnce(
            mockListResponse(meetings, 'cursor-2'),
        );

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings,
                nextPageToken: 'cursor-2',
                isRefreshing: false,
                isLoadingMore: false,
            });
        });

        expect(mockedGetMe).toHaveBeenCalledTimes(1);
        expect(mockedListParticipatedMeetings).toHaveBeenCalledWith({
            path: { userId: 'user-1' },
            query: {
                pageSize: 20,
                pageToken: undefined,
                status: 'ENDED,CANCELLED',
            },
            throwOnError: true,
        });
    });

    it('enters EMPTY when the initial page has no meetings', async () => {
        mockedListParticipatedMeetings.mockResolvedValueOnce(
            mockListResponse([]),
        );

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state).toEqual({ phase: 'EMPTY' });
        });
    });

    it('enters ERROR when the initial load fails', async () => {
        mockedListParticipatedMeetings.mockRejectedValueOnce(
            new ApiError('Backend unavailable'),
        );

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'ERROR',
                message: 'Backend unavailable',
            });
        });
    });

    it('surfaces the session-expired message when getMe fails with an auth error', async () => {
        mockedGetMe.mockRejectedValueOnce(
            new ApiFailError('TOKEN_EXPIRED', 'Expired'),
        );

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'ERROR',
                message: 'Please sign in again.',
            });
        });

        expect(mockedListParticipatedMeetings).not.toHaveBeenCalled();
    });

    it('keeps the current list during refresh and replaces it on success', async () => {
        const initialMeetings = [
            buildMeeting({ id: 'meeting-1', title: 'Old title' }),
        ];
        const refreshedMeetings = [
            buildMeeting({ id: 'meeting-2', title: 'New title' }),
        ];
        const refreshRequest =
            deferred<Awaited<ReturnType<typeof listParticipatedMeetings>>>();

        mockedListParticipatedMeetings
            .mockResolvedValueOnce(
                mockListResponse(initialMeetings, 'cursor-2'),
            )
            .mockImplementationOnce(() => refreshRequest.promise);

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.refresh();
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: initialMeetings,
                nextPageToken: 'cursor-2',
                isRefreshing: true,
                isLoadingMore: false,
            });
        });

        await act(async () => {
            refreshRequest.resolve(mockListResponse(refreshedMeetings, null));
            await refreshRequest.promise;
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: refreshedMeetings,
                nextPageToken: null,
                isRefreshing: false,
                isLoadingMore: false,
            });
        });

        expect(mockedGetMe).toHaveBeenCalledTimes(1);
        expect(mockedListParticipatedMeetings).toHaveBeenNthCalledWith(2, {
            path: { userId: 'user-1' },
            query: {
                pageSize: 20,
                pageToken: undefined,
                status: 'ENDED,CANCELLED',
            },
            throwOnError: true,
        });
    });

    it('restores the previous list and shows a toast when refresh fails', async () => {
        const initialMeetings = [buildMeeting()];

        mockedListParticipatedMeetings
            .mockResolvedValueOnce(
                mockListResponse(initialMeetings, 'cursor-2'),
            )
            .mockRejectedValueOnce(new ApiError('Refresh failed'));

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.refresh();
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: initialMeetings,
                nextPageToken: 'cursor-2',
                isRefreshing: false,
                isLoadingMore: false,
            });
        });

        expect(toastError).toHaveBeenCalledWith(
            'Could not refresh meeting history',
            {
                description:
                    'Your current list is still shown. Try refreshing again in a moment.',
            },
        );
    });

    it('appends meetings and updates the cursor on loadMore success', async () => {
        const initialMeetings = [buildMeeting({ id: 'meeting-1' })];
        const nextMeetings = [buildMeeting({ id: 'meeting-2' })];

        mockedListParticipatedMeetings
            .mockResolvedValueOnce(
                mockListResponse(initialMeetings, 'cursor-2'),
            )
            .mockResolvedValueOnce(mockListResponse(nextMeetings, 'cursor-3'));

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.loadMore();
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: [...initialMeetings, ...nextMeetings],
                nextPageToken: 'cursor-3',
                isRefreshing: false,
                isLoadingMore: false,
            });
        });

        expect(mockedListParticipatedMeetings).toHaveBeenNthCalledWith(2, {
            path: { userId: 'user-1' },
            query: {
                pageSize: 20,
                pageToken: 'cursor-2',
                status: 'ENDED,CANCELLED',
            },
            throwOnError: true,
        });
    });

    it('does nothing when loadMore is called without a nextPageToken', async () => {
        const meetings = [buildMeeting()];
        mockedListParticipatedMeetings.mockResolvedValueOnce(
            mockListResponse(meetings),
        );

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings,
                nextPageToken: null,
                isRefreshing: false,
                isLoadingMore: false,
            });
        });

        act(() => {
            result.current.loadMore();
        });

        expect(mockedListParticipatedMeetings).toHaveBeenCalledTimes(1);
        expect(toastError).not.toHaveBeenCalled();
    });

    it('keeps the current list and shows a retry toast when loadMore fails', async () => {
        const initialMeetings = [buildMeeting()];

        mockedListParticipatedMeetings
            .mockResolvedValueOnce(
                mockListResponse(initialMeetings, 'cursor-2'),
            )
            .mockRejectedValueOnce(new ApiError('Load more failed'));

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.loadMore();
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: initialMeetings,
                nextPageToken: 'cursor-2',
                isRefreshing: false,
                isLoadingMore: false,
            });
        });

        expect(toastError).toHaveBeenCalledWith(
            'Could not load more meetings',
            {
                description:
                    'Your current list is still available. Try loading the next page again.',
                action: {
                    label: 'Retry',
                    onClick: expect.any(Function),
                },
            },
        );
    });

    it('guards against double invocation while loadMore is already in flight', async () => {
        const initialMeetings = [buildMeeting()];
        const loadMoreRequest =
            deferred<Awaited<ReturnType<typeof listParticipatedMeetings>>>();

        mockedListParticipatedMeetings
            .mockResolvedValueOnce(
                mockListResponse(initialMeetings, 'cursor-2'),
            )
            .mockImplementationOnce(() => loadMoreRequest.promise);

        const { result } = renderHook(() => useMeetingHistory());

        await waitFor(() => {
            expect(result.current.state.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.loadMore();
            result.current.loadMore();
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: initialMeetings,
                nextPageToken: 'cursor-2',
                isRefreshing: false,
                isLoadingMore: true,
            });
        });

        expect(mockedListParticipatedMeetings).toHaveBeenCalledTimes(2);

        await act(async () => {
            loadMoreRequest.resolve(
                mockListResponse([buildMeeting({ id: 'meeting-2' })]),
            );
            await loadMoreRequest.promise;
        });

        await waitFor(() => {
            expect(result.current.state).toEqual({
                phase: 'SUCCESS',
                meetings: [
                    ...initialMeetings,
                    buildMeeting({ id: 'meeting-2' }),
                ],
                nextPageToken: null,
                isRefreshing: false,
                isLoadingMore: false,
            });
        });
    });
});
