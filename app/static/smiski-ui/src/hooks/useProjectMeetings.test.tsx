// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const listProjectMeetingsMock = vi.fn();
vi.mock('../api/meetings', () => ({
    DEFAULT_MEETING_PAGE_SIZE: 20,
    listProjectMeetings: (...args: unknown[]) =>
        listProjectMeetingsMock(...args),
}));

import { useProjectMeetings } from './useProjectMeetings';

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

describe('useProjectMeetings', () => {
    beforeEach(() => listProjectMeetingsMock.mockReset());

    it('navigates cursor pages and resets to page one when filters change', async () => {
        listProjectMeetingsMock
            .mockResolvedValueOnce({
                meetings: [{ id: 'meeting-1' }],
                size: 1,
                hasNext: true,
                nextPageToken: 'page-2',
            })
            .mockResolvedValueOnce({
                meetings: [{ id: 'meeting-2' }],
                size: 1,
                hasNext: false,
            })
            .mockResolvedValueOnce({
                meetings: [{ id: 'meeting-3' }],
                size: 1,
                hasNext: false,
            })
            .mockResolvedValue({
                meetings: [{ id: 'meeting-4' }],
                size: 1,
                hasNext: false,
            });

        const { result, rerender } = renderHook(
            ({ search }) =>
                useProjectMeetings({ projectKey: 'SMISKI', search }),
            {
                initialProps: { search: undefined as string | undefined },
                wrapper: createWrapper(),
            },
        );

        await waitFor(() => expect(result.current.hasNextPage).toBe(true));
        act(() => result.current.nextPage());

        await waitFor(() => expect(result.current.pageNumber).toBe(2));
        await waitFor(() =>
            expect(result.current.meetings[0]?.id).toBe('meeting-2'),
        );
        expect(listProjectMeetingsMock.mock.calls[1][0]).toMatchObject({
            pageToken: 'page-2',
            pageSize: 20,
        });

        act(() => result.current.previousPage());
        await waitFor(() => expect(result.current.pageNumber).toBe(1));

        rerender({ search: 'planning' });
        await waitFor(() =>
            expect(listProjectMeetingsMock).toHaveBeenLastCalledWith(
                expect.objectContaining({
                    search: 'planning',
                    pageToken: undefined,
                }),
            ),
        );
        expect(result.current.pageNumber).toBe(1);
    });

    it('omits creatorId for All creators and sends an explicit selection', async () => {
        listProjectMeetingsMock.mockResolvedValue({
            meetings: [],
            size: 0,
            hasNext: false,
        });

        const { rerender } = renderHook(
            ({ createdByAccountId }) =>
                useProjectMeetings({
                    projectKey: 'SMISKI',
                    createdByAccountId,
                }),
            {
                initialProps: {
                    createdByAccountId: undefined as string | undefined,
                },
                wrapper: createWrapper(),
            },
        );

        await waitFor(() => expect(listProjectMeetingsMock).toHaveBeenCalled());
        expect(listProjectMeetingsMock.mock.calls[0][0]).toMatchObject({
            projectKey: 'SMISKI',
            createdByAccountId: undefined,
        });

        rerender({ createdByAccountId: 'account-alice' });
        await waitFor(() =>
            expect(listProjectMeetingsMock).toHaveBeenLastCalledWith(
                expect.objectContaining({
                    createdByAccountId: 'account-alice',
                    pageToken: undefined,
                }),
            ),
        );
    });
});
