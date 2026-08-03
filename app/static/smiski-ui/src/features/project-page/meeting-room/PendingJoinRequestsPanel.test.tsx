// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const hooks = vi.hoisted(() => ({
    accept: { isPending: false, mutate: vi.fn() },
    decline: { isPending: false, mutate: vi.fn() },
    pending: {
        data: {
            requests: [
                {
                    requestId: 'request-1',
                    accountId: 'account-1',
                    displayName: 'Alice',
                    status: 'PENDING',
                    requestedAt: '',
                    expiresAt: '',
                },
            ],
            total: 1,
            offset: 0,
            pageSize: 20,
        },
        isLoading: false,
    },
}));

vi.mock('../../../hooks/useJoinRequests', () => ({
    useAcceptJoinRequests: () => hooks.accept,
    useDeclineJoinRequests: () => hooks.decline,
    usePendingJoinRequests: () => hooks.pending,
}));

import { PendingJoinRequestsPanel } from './PendingJoinRequestsPanel';

describe('PendingJoinRequestsPanel', () => {
    beforeEach(() => {
        hooks.accept.mutate.mockClear();
        hooks.decline.mutate.mockClear();
    });

    it('submits host decisions for the selected request', () => {
        render(<PendingJoinRequestsPanel meetingId='meeting-1' />);

        expect(screen.getByText('Alice')).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Accept' }));
        fireEvent.click(screen.getByRole('button', { name: 'Decline' }));

        expect(hooks.accept.mutate).toHaveBeenCalledWith({
            meetingId: 'meeting-1',
            requestIds: ['request-1'],
        });
        expect(hooks.decline.mutate).toHaveBeenCalledWith({
            meetingId: 'meeting-1',
            requestIds: ['request-1'],
        });
    });
});
