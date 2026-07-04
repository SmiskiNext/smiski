import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiFailError } from '@/lib/api/types.ts';

vi.mock('@/generated/sdk.gen.ts', () => ({
    cancelMeeting: vi.fn(),
    listHostMeetings: vi.fn(),
}));

import { cancelMeeting, listHostMeetings } from '@/generated/sdk.gen.ts';
import { useUpcomingMeetings } from './use-upcoming-meetings.ts';

const mockedCancelMeeting = vi.mocked(cancelMeeting);
const mockedListHostMeetings = vi.mocked(listHostMeetings);

const SCHEDULED_MEETING = {
    id: 'meeting-1',
    hostId: 'host-1',
    shortCode: 'ABC1234567',
    title: 'Planning',
    startTime: '2026-06-01T10:00:00Z',
    type: 'SCHEDULED' as const,
    status: 'SCHEDULED' as const,
};

const MESSAGES = {
    errorFallback: 'fallback message',
    statusConflict: 'meeting already running, list refreshed',
};

function mockListResponse(meetings: (typeof SCHEDULED_MEETING)[]) {
    return {
        data: { content: meetings, pageSize: 20, nextPageToken: null },
    } as unknown as Awaited<ReturnType<typeof listHostMeetings>>;
}

beforeEach(() => {
    vi.clearAllMocks();
});

describe('useUpcomingMeetings.confirmCancel', () => {
    it('shows the friendly status conflict message and reloads the list when backend rejects with INVALID_STATUS_TRANSITION', async () => {
        mockedListHostMeetings.mockResolvedValue(
            mockListResponse([SCHEDULED_MEETING]),
        );
        mockedCancelMeeting.mockRejectedValueOnce(
            new ApiFailError(
                'INVALID_STATUS_TRANSITION',
                'Cannot transition meeting from LIVE to CANCELLED',
            ),
        );

        const { result } = renderHook(() => useUpcomingMeetings());

        await waitFor(() => {
            expect(result.current.listState.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.actions.requestCancel(SCHEDULED_MEETING);
        });

        await act(async () => {
            await result.current.actions.confirmCancel(MESSAGES);
        });

        expect(result.current.cancelError).toBe(MESSAGES.statusConflict);
        expect(mockedListHostMeetings).toHaveBeenCalledTimes(2);
        expect(result.current.cancelTarget).not.toBeNull();
    });

    it('shows the api error message for non-conflict ApiFailError responses', async () => {
        mockedListHostMeetings.mockResolvedValue(
            mockListResponse([SCHEDULED_MEETING]),
        );
        mockedCancelMeeting.mockRejectedValueOnce(
            new ApiFailError('SOME_OTHER', 'Other error'),
        );

        const { result } = renderHook(() => useUpcomingMeetings());

        await waitFor(() => {
            expect(result.current.listState.phase).toBe('SUCCESS');
        });

        act(() => {
            result.current.actions.requestCancel(SCHEDULED_MEETING);
        });

        await act(async () => {
            await result.current.actions.confirmCancel(MESSAGES);
        });

        expect(result.current.cancelError).toBe('Other error');
        expect(mockedListHostMeetings).toHaveBeenCalledTimes(1);
    });
});
