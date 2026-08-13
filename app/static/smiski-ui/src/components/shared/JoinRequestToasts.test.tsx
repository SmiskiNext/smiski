// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { JoinRequestToast } from '../../hooks/useJoinRequestNotifications';
import { JoinRequestToasts } from './JoinRequestToasts';

afterEach(cleanup);

const TOAST: JoinRequestToast = {
    id: 'toast-1',
    requestId: 'request-1',
    displayName: 'Alice',
    avatarUrl: 'https://avatar.example/alice.png',
};

describe('JoinRequestToasts', () => {
    it('renders nothing for an empty list', () => {
        const { container } = render(
            <JoinRequestToasts
                toasts={[]}
                onAccept={vi.fn()}
                onDecline={vi.fn()}
                onOpenPanel={vi.fn()}
            />,
        );

        expect(container.firstChild).toBeNull();
    });

    it('renders the waiting copy and avatar', () => {
        render(
            <JoinRequestToasts
                toasts={[TOAST]}
                onAccept={vi.fn()}
                onDecline={vi.fn()}
                onOpenPanel={vi.fn()}
            />,
        );

        expect(screen.getByText('Alice')).toBeDefined();
        expect(screen.getByText('is waiting to join')).toBeDefined();
        expect(screen.getByRole('img', { name: 'Alice' })).toHaveProperty(
            'src',
            TOAST.avatarUrl,
        );
    });

    it('opens the pending panel when the toast body is clicked', () => {
        const onOpenPanel = vi.fn();
        render(
            <JoinRequestToasts
                toasts={[TOAST]}
                onAccept={vi.fn()}
                onDecline={vi.fn()}
                onOpenPanel={onOpenPanel}
            />,
        );

        fireEvent.click(screen.getByText('is waiting to join'));

        expect(onOpenPanel).toHaveBeenCalledTimes(1);
    });

    it('does not open the panel when a decision button is clicked', () => {
        const onOpenPanel = vi.fn();
        const onAccept = vi.fn();
        const onDecline = vi.fn();
        render(
            <JoinRequestToasts
                toasts={[TOAST]}
                onAccept={onAccept}
                onDecline={onDecline}
                onOpenPanel={onOpenPanel}
            />,
        );

        fireEvent.click(screen.getByRole('button', { name: 'Accept' }));
        fireEvent.click(screen.getByRole('button', { name: 'Decline' }));

        expect(onAccept).toHaveBeenCalledWith('request-1');
        expect(onDecline).toHaveBeenCalledWith('request-1');
        expect(onOpenPanel).not.toHaveBeenCalled();
    });
});
