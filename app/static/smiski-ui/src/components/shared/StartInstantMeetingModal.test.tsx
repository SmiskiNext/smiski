// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { CreateInstantMeetingResult } from '../../api/meetings';
import { StartInstantMeetingModal } from './StartInstantMeetingModal';

const mutateAsync = vi.fn<() => Promise<CreateInstantMeetingResult>>();

vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), invokeRemote: vi.fn() }));

vi.mock('../../hooks/useMeetingMutations', () => ({
    useCreateInstantMeeting: () => ({
        mutateAsync,
        isPending: false,
    }),
}));

vi.mock('../../context/CurrentUserContext', () => ({
    useCurrentUser: () => ({
        accountId: 'acc-host',
        displayName: 'Host User',
        email: 'host@example.com',
        avatarUrl: undefined,
        timeZone: 'Asia/Ho_Chi_Minh',
    }),
}));

vi.mock('./WorkspaceUserPicker', () => ({
    WorkspaceUserPicker: () => null,
}));

beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
            matches: false,
            media: query,
            onchange: null,
            addListener: vi.fn(),
            removeListener: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn(),
        }),
    });
    window.ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
    } as unknown as typeof ResizeObserver;
});

afterEach(() => {
    cleanup();
    mutateAsync.mockReset();
});

function renderModal() {
    const onStarted = vi.fn();
    const onClose = vi.fn();
    render(
        <StartInstantMeetingModal
            isOpen
            projectKey='SMISKI'
            issueId='10001'
            issueKey='SMISKI-101'
            onClose={onClose}
            onStarted={onStarted}
        />,
    );
    return { onStarted, onClose };
}

describe('StartInstantMeetingModal result handling', () => {
    it('keeps the modal open and shows the message when the result carries an error', async () => {
        mutateAsync.mockResolvedValue({
            error: { message: 'title must not be blank' },
        });
        const user = userEvent.setup();
        const { onStarted, onClose } = renderModal();

        await user.type(
            screen.getByPlaceholderText('e.g. Investigate deployment failure'),
            'Incident sync',
        );
        await user.click(screen.getByRole('button', { name: 'Start meeting' }));

        await waitFor(() => {
            expect(screen.getByText('title must not be blank')).toBeDefined();
        });
        expect(onStarted).not.toHaveBeenCalled();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('reports the created meeting id and closes when the result carries data', async () => {
        mutateAsync.mockResolvedValue({
            data: {
                id: 'meeting-1',
                title: 'Incident sync',
                projectId: 'project-smiski',
                projectKey: 'SMISKI',
                issueId: 'issue-SMISKI-101',
                issueKey: 'SMISKI-101',
                creatorId: 'acc-host',
                creatorName: 'Host User',
                hostId: 'acc-host',
                hostName: 'Host User',
                status: 'RUNNING',
                participantCount: 1,
            },
        });
        const user = userEvent.setup();
        const { onStarted, onClose } = renderModal();

        await user.type(
            screen.getByPlaceholderText('e.g. Investigate deployment failure'),
            'Incident sync',
        );
        await user.click(screen.getByRole('button', { name: 'Start meeting' }));

        await waitFor(() => {
            expect(onStarted).toHaveBeenCalledWith('meeting-1');
        });
        expect(mutateAsync).toHaveBeenCalledWith(
            expect.objectContaining({
                issueId: '10001',
                issueKey: 'SMISKI-101',
            }),
        );
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
