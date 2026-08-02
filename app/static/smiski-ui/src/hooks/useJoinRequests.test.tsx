// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
    acceptPendingMeetingJoinRequests: vi.fn(),
    declinePendingMeetingJoinRequests: vi.fn(),
    listPendingMeetingJoinRequests: vi.fn(),
}));

vi.mock('../api/meetings', () => apiMocks);

import { queryKeys } from './queryKeys';
import {
    DEFAULT_JOIN_REQUEST_POLL_INTERVAL_MS,
    useAcceptJoinRequests,
    useDeclineJoinRequests,
    usePendingJoinRequests,
} from './useJoinRequests';

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
const REQUEST_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91';
const EMPTY_PAGE = { requests: [], total: 0, offset: 0, pageSize: 20 };

function createHarness() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });
    const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    );
    return { queryClient, wrapper };
}

describe('manual-admission hooks', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        apiMocks.listPendingMeetingJoinRequests.mockResolvedValue(EMPTY_PAGE);
        apiMocks.acceptPendingMeetingJoinRequests.mockResolvedValue([]);
        apiMocks.declinePendingMeetingJoinRequests.mockResolvedValue([]);
    });

    it('loads the requested pending page and configures host-side polling', async () => {
        const { queryClient, wrapper } = createHarness();
        const params = { offset: 20, pageSize: 10 };
        const { result } = renderHook(
            () => usePendingJoinRequests(MEETING_ID, params),
            { wrapper },
        );

        await waitFor(() => expect(result.current.isSuccess).toBe(true));

        expect(apiMocks.listPendingMeetingJoinRequests).toHaveBeenCalledWith(
            MEETING_ID,
            params,
        );
        const query = queryClient.getQueryCache().find({
            queryKey: queryKeys.pendingJoinRequestsPage(MEETING_ID, params),
        });
        if (!query) throw new Error('Expected pending join-request query.');
        expect(
            (query.options as { refetchInterval?: number }).refetchInterval,
        ).toBe(DEFAULT_JOIN_REQUEST_POLL_INTERVAL_MS);
    });

    it('does not issue a request without a meeting id', () => {
        const { wrapper } = createHarness();
        const { result } = renderHook(() => usePendingJoinRequests(), {
            wrapper,
        });

        expect(result.current.fetchStatus).toBe('idle');
        expect(apiMocks.listPendingMeetingJoinRequests).not.toHaveBeenCalled();
    });

    it('accepts requests then invalidates every pending page for the meeting', async () => {
        const { queryClient, wrapper } = createHarness();
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
        const { result } = renderHook(() => useAcceptJoinRequests(), {
            wrapper,
        });

        await act(async () => {
            await result.current.mutateAsync({
                meetingId: MEETING_ID,
                requestIds: [REQUEST_ID],
            });
        });

        expect(apiMocks.acceptPendingMeetingJoinRequests).toHaveBeenCalledWith(
            MEETING_ID,
            [REQUEST_ID],
        );
        expect(invalidate).toHaveBeenCalledWith({
            queryKey: queryKeys.pendingJoinRequests(MEETING_ID),
        });
    });

    it('declines requests then invalidates every pending page for the meeting', async () => {
        const { queryClient, wrapper } = createHarness();
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
        const { result } = renderHook(() => useDeclineJoinRequests(), {
            wrapper,
        });

        await act(async () => {
            await result.current.mutateAsync({
                meetingId: MEETING_ID,
                requestIds: [REQUEST_ID],
            });
        });

        expect(apiMocks.declinePendingMeetingJoinRequests).toHaveBeenCalledWith(
            MEETING_ID,
            [REQUEST_ID],
        );
        expect(invalidate).toHaveBeenCalledWith({
            queryKey: queryKeys.pendingJoinRequests(MEETING_ID),
        });
    });
});
