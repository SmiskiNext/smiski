// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

const hooks = vi.hoisted(() => ({
    useMeeting: vi.fn(),
    updateSettings: { mutateAsync: vi.fn(), isPending: false },
}));

vi.mock('../../hooks/useMeeting', () => ({ useMeeting: hooks.useMeeting }));

vi.mock('../../hooks/useMeetingMutations', () => ({
    useUpdateMeetingSettings: () => hooks.updateSettings,
}));

import { MeetingSettingsModal } from './MeetingSettingsModal';

const MEETING_ID = 'meeting-1';
const NOTIFICATIONS_LABEL = 'Join and leave notifications';
const ROOM_SETTINGS_LABEL = 'Who can join';

const SETTINGS = {
    admissionPolicy: 'ALLOW_ALL' as const,
    maxParticipants: 20,
    allowScreenShare: true,
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: true,
};

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
    vi.clearAllMocks();
});

function renderModal(
    props: Partial<Parameters<typeof MeetingSettingsModal>[0]> = {},
) {
    const onNotificationsEnabledChange = vi.fn();
    render(
        <MeetingSettingsModal
            isOpen
            meetingId={MEETING_ID}
            onClose={vi.fn()}
            onNotificationsEnabledChange={onNotificationsEnabledChange}
            {...props}
        />,
    );
    return { onNotificationsEnabledChange };
}

describe('MeetingSettingsModal sections', () => {
    it('shows only the notifications toggle to a non-host, with no Save action', () => {
        hooks.useMeeting.mockReturnValue({
            meeting: null,
            loading: false,
            error: null,
        });

        renderModal({ showNotificationPreferences: true, isHost: false });

        expect(screen.getByText(NOTIFICATIONS_LABEL)).toBeDefined();
        expect(screen.queryByText(ROOM_SETTINGS_LABEL)).toBeNull();
        expect(
            screen.queryByRole('button', { name: 'Save settings' }),
        ).toBeNull();
        expect(screen.getByRole('button', { name: 'Done' })).toBeDefined();
    });

    it('does not fetch meeting detail for a non-host', () => {
        hooks.useMeeting.mockReturnValue({
            meeting: null,
            loading: false,
            error: null,
        });

        renderModal({ showNotificationPreferences: true, isHost: false });

        expect(hooks.useMeeting).toHaveBeenCalledWith(undefined);
    });

    it('shows both sections plus Save to a host in the room', () => {
        hooks.useMeeting.mockReturnValue({
            meeting: { id: MEETING_ID, settings: SETTINGS },
            loading: false,
            error: null,
        });

        renderModal({ showNotificationPreferences: true, isHost: true });

        expect(screen.getByText(NOTIFICATIONS_LABEL)).toBeDefined();
        expect(screen.getByText(ROOM_SETTINGS_LABEL)).toBeDefined();
        expect(
            screen.getByRole('button', { name: 'Save settings' }),
        ).toBeDefined();
        expect(hooks.useMeeting).toHaveBeenCalledWith(MEETING_ID);
    });

    it('omits the notifications section unless the caller opts in', () => {
        hooks.useMeeting.mockReturnValue({
            meeting: { id: MEETING_ID, settings: SETTINGS },
            loading: false,
            error: null,
        });

        renderModal();

        expect(screen.queryByText(NOTIFICATIONS_LABEL)).toBeNull();
        expect(screen.getByText(ROOM_SETTINGS_LABEL)).toBeDefined();
        expect(
            screen.getByRole('button', { name: 'Save settings' }),
        ).toBeDefined();
    });

    it('reports a flipped notification toggle straight to the caller', () => {
        hooks.useMeeting.mockReturnValue({
            meeting: null,
            loading: false,
            error: null,
        });

        const { onNotificationsEnabledChange } = renderModal({
            showNotificationPreferences: true,
            isHost: false,
            notificationsEnabled: true,
        });

        fireEvent.click(screen.getByRole('switch'));

        expect(onNotificationsEnabledChange).toHaveBeenCalledWith(
            false,
            expect.anything(),
        );
    });
});
