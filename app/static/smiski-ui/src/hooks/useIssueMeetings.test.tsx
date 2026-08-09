// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const listIssueMeetingsMock = vi.fn();
vi.mock('../api/meetings', () => ({
    DEFAULT_MEETING_PAGE_SIZE: 20,
    listIssueMeetings: (...args: unknown[]) => listIssueMeetingsMock(...args),
}));
vi.mock('../context/CurrentUserContext', () => ({
    useCurrentUser: () => ({
        accountId: 'current-user',
        displayName: 'Current User',
    }),
}));
vi.mock('./useProjectMembers', () => ({
    useProjectMembers: () => ({ members: [], loading: false, error: null }),
}));

import { useIssueMeetings } from './useIssueMeetings';

function createWrapper() {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    });
    return function QueryWrapper({ children }: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    };
}

describe('useIssueMeetings', () => {
    beforeEach(() => listIssueMeetingsMock.mockReset());

    it('accumulates issue pages through load more', async () => {
        const firstPage = Array.from({ length: 20 }, (_, index) => ({
            id: `meeting-${index + 1}`,
            hostId: 'current-user',
        }));
        listIssueMeetingsMock
            .mockResolvedValueOnce({
                meetings: firstPage,
                total: 21,
                offset: 0,
                pageSize: 20,
                hasNext: true,
            })
            .mockResolvedValueOnce({
                meetings: [{ id: 'meeting-21', hostId: 'current-user' }],
                total: 21,
                offset: 20,
                pageSize: 20,
                hasNext: false,
            });

        const { result } = renderHook(
            () => useIssueMeetings('10001', 'SMISKI'),
            { wrapper: createWrapper() },
        );

        await waitFor(() => expect(result.current.meetings).toHaveLength(20));
        expect(result.current.hasMore).toBe(true);
        act(() => result.current.loadMore());

        await waitFor(() => expect(result.current.meetings).toHaveLength(21));
        expect(result.current.total).toBe(21);
        expect(result.current.hasMore).toBe(false);
        expect(listIssueMeetingsMock).toHaveBeenNthCalledWith(2, '10001', {
            offset: 20,
            pageSize: 20,
        });
    });
});
