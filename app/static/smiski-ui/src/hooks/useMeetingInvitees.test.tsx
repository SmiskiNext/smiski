// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
    addMeetingInvitees: vi.fn(),
    getMeeting: vi.fn(),
    removeMeetingInvitees: vi.fn(),
}));

vi.mock('../api/meetings', () => apiMocks);

import { queryKeys } from './queryKeys';
import {
    useAddMeetingInvitees,
    useMeetingInvitees,
    useRemoveMeetingInvitees,
} from './useMeetingInvitees';

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
const INVITEE = {
    id: 'invitee-1',
    accountId: 'account-456',
    email: 'alice@example.com',
    displayName: 'Alice Nguyen',
    status: 'NEEDS_ACTION' as const,
};

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

describe('meeting invitee hooks', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        apiMocks.getMeeting.mockResolvedValue({
            meeting: { id: MEETING_ID },
            invitees: [INVITEE],
            participants: [],
        });
        apiMocks.addMeetingInvitees.mockResolvedValue([INVITEE]);
        apiMocks.removeMeetingInvitees.mockResolvedValue([INVITEE]);
    });

    it('selects invitees from the shared meeting-detail query', async () => {
        const { wrapper } = createHarness();
        const { result } = renderHook(() => useMeetingInvitees(MEETING_ID), {
            wrapper,
        });

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.invitees).toEqual([INVITEE]);
        expect(apiMocks.getMeeting).toHaveBeenCalledWith(MEETING_ID);
    });

    it('adds invitees then invalidates detail and meeting lists', async () => {
        const { queryClient, wrapper } = createHarness();
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
        const { result } = renderHook(() => useAddMeetingInvitees(), {
            wrapper,
        });
        const inviteeInput = {
            accountId: INVITEE.accountId,
            email: INVITEE.email,
            displayName: INVITEE.displayName,
        };

        await act(async () => {
            await result.current.mutateAsync({
                meetingId: MEETING_ID,
                invitees: [inviteeInput],
            });
        });

        expect(apiMocks.addMeetingInvitees).toHaveBeenCalledWith(MEETING_ID, [
            inviteeInput,
        ]);
        expect(invalidate).toHaveBeenCalledWith({
            queryKey: queryKeys.meeting(MEETING_ID),
        });
        expect(invalidate).toHaveBeenCalledWith({ queryKey: ['meetings'] });
    });

    it('removes invitees by invitee id and invalidates shared caches', async () => {
        const { queryClient, wrapper } = createHarness();
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
        const { result } = renderHook(() => useRemoveMeetingInvitees(), {
            wrapper,
        });

        await act(async () => {
            await result.current.mutateAsync({
                meetingId: MEETING_ID,
                inviteeIds: [INVITEE.id],
            });
        });

        expect(apiMocks.removeMeetingInvitees).toHaveBeenCalledWith(
            MEETING_ID,
            [INVITEE.id],
        );
        expect(invalidate).toHaveBeenCalledWith({
            queryKey: queryKeys.meeting(MEETING_ID),
        });
    });
});
