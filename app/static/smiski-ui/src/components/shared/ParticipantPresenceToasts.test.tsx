// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ParticipantPresenceToast } from '../../hooks/useParticipantPresenceNotifications';
import { ParticipantPresenceToasts } from './ParticipantPresenceToasts';

afterEach(cleanup);

describe('ParticipantPresenceToasts', () => {
    it('renders nothing for an empty list', () => {
        const { container } = render(
            <ParticipantPresenceToasts toasts={[]} onDismiss={vi.fn()} />,
        );

        expect(container.firstChild).toBeNull();
    });

    it('renders a joined toast with the participant name', () => {
        const toasts: ParticipantPresenceToast[] = [
            { id: '1', kind: 'joined', displayName: 'Alice' },
        ];

        render(
            <ParticipantPresenceToasts toasts={toasts} onDismiss={vi.fn()} />,
        );

        expect(screen.getByText('Alice')).toBeDefined();
        expect(screen.getByText('joined')).toBeDefined();
    });

    it('renders a left toast with the participant name', () => {
        const toasts: ParticipantPresenceToast[] = [
            { id: '1', kind: 'left', displayName: 'Bob' },
        ];

        render(
            <ParticipantPresenceToasts toasts={toasts} onDismiss={vi.fn()} />,
        );

        expect(screen.getByText('Bob')).toBeDefined();
        expect(screen.getByText('left')).toBeDefined();
    });

    it('calls onDismiss with the toast id when its close button is clicked', () => {
        const onDismiss = vi.fn();
        const toasts: ParticipantPresenceToast[] = [
            { id: 'toast-1', kind: 'joined', displayName: 'Alice' },
            { id: 'toast-2', kind: 'left', displayName: 'Bob' },
        ];

        render(
            <ParticipantPresenceToasts toasts={toasts} onDismiss={onDismiss} />,
        );

        const dismissButtons = screen.getAllByRole('button', {
            name: 'Dismiss',
        });
        fireEvent.click(dismissButtons[1]);

        expect(onDismiss).toHaveBeenCalledWith('toast-2');
    });

    it('exposes the container with a status role for assistive technology', () => {
        const toasts: ParticipantPresenceToast[] = [
            { id: '1', kind: 'joined', displayName: 'Alice' },
        ];

        render(
            <ParticipantPresenceToasts toasts={toasts} onDismiss={vi.fn()} />,
        );

        expect(screen.getByRole('status')).toBeDefined();
    });
});
