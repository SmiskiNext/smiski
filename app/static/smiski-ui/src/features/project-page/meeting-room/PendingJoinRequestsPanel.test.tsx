// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PendingJoinRequest } from '../../../domain';
import { PendingJoinRequestsPanel } from './PendingJoinRequestsPanel';

afterEach(cleanup);

const REQUEST: PendingJoinRequest = {
    requestId: 'request-1',
    accountId: 'account-1',
    displayName: 'Alice',
    status: 'PENDING',
    requestedAt: '',
    expiresAt: '',
    avatarUrl: 'https://avatar.example/alice.png',
};

describe('PendingJoinRequestsPanel', () => {
    it('renders avatars and submits host decisions for the selected request', () => {
        const onAccept = vi.fn();
        const onDecline = vi.fn();
        render(
            <PendingJoinRequestsPanel
                requests={[REQUEST]}
                total={1}
                isLoading={false}
                isDeciding={false}
                onAccept={onAccept}
                onDecline={onDecline}
            />,
        );

        expect(screen.getByText('Alice')).toBeTruthy();
        expect(screen.getByRole('img', { name: 'Alice' })).toHaveProperty(
            'src',
            REQUEST.avatarUrl,
        );
        fireEvent.click(screen.getByRole('button', { name: 'Accept' }));
        fireEvent.click(screen.getByRole('button', { name: 'Decline' }));

        expect(onAccept).toHaveBeenCalledWith('request-1');
        expect(onDecline).toHaveBeenCalledWith('request-1');
    });

    it('shows a loading state while the first fetch is in flight', () => {
        render(
            <PendingJoinRequestsPanel
                requests={[]}
                total={0}
                isLoading
                isDeciding={false}
                onAccept={vi.fn()}
                onDecline={vi.fn()}
            />,
        );

        expect(screen.getByLabelText('Loading join requests…')).toBeTruthy();
    });

    it('shows an empty state when nobody is waiting', () => {
        render(
            <PendingJoinRequestsPanel
                requests={[]}
                total={0}
                isLoading={false}
                isDeciding={false}
                onAccept={vi.fn()}
                onDecline={vi.fn()}
            />,
        );

        expect(screen.getByText('No one is waiting to join.')).toBeTruthy();
    });

    it('disables decision buttons while a mutation is pending', () => {
        render(
            <PendingJoinRequestsPanel
                requests={[REQUEST]}
                total={1}
                isLoading={false}
                isDeciding
                onAccept={vi.fn()}
                onDecline={vi.fn()}
            />,
        );

        expect(screen.getByRole('button', { name: 'Accept' })).toHaveProperty(
            'disabled',
            true,
        );
        expect(screen.getByRole('button', { name: 'Decline' })).toHaveProperty(
            'disabled',
            true,
        );
    });
});
