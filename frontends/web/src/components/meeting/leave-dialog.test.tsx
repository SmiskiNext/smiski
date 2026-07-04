import {
    act,
    fireEvent,
    render,
    screen,
    waitFor,
} from '@testing-library/react';
import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LeaveDialog } from './leave-dialog.tsx';

const TRANSLATIONS: Record<string, string> = {
    leaveDialogTitle: 'Leave Meeting',
    leaveDialogMessage: 'Are you sure you want to leave this meeting?',
    leaveDialogConfirm: 'Leave',
    leaveDialogCancel: 'Stay',
    hostLeaveDialogTitle: 'Leave Meeting',
    hostLeaveDialogMessage: 'Choose how you want to exit this meeting.',
    hostLeaveDialogLeave: 'Leave Meeting',
    hostLeaveDialogEndForAll: 'End for All',
    hostLeaveDialogEndForAllLoading: 'Ending...',
    hostLeaveDialogEndError: 'Failed to end the meeting. Please try again.',
};

vi.mock('next-intl', () => ({
    useTranslations: () => (key: string) => {
        return key in TRANSLATIONS ? TRANSLATIONS[key] : key;
    },
    useLocale: () => 'en',
}));

const mockDisconnect = vi.fn().mockResolvedValue(undefined);

vi.mock('@livekit/components-react', () => ({
    useRoomContext: () => ({ disconnect: mockDisconnect }),
}));

vi.mock('@/generated/sdk.gen.ts', () => ({
    endMeeting: vi.fn(),
}));

import { endMeeting } from '@/generated/sdk.gen.ts';

const mockedEndMeeting = vi.mocked(endMeeting);

let mockLocationHref = '';

beforeEach(() => {
    vi.clearAllMocks();
    mockDisconnect.mockResolvedValue(undefined);
    mockLocationHref = '';
    Object.defineProperty(window, 'location', {
        configurable: true,
        value: {
            ...window.location,
            get href() {
                return mockLocationHref;
            },
            set href(value: string) {
                mockLocationHref = value;
            },
        },
    });
});

function renderDialog(props: Partial<ComponentProps<typeof LeaveDialog>> = {}) {
    const defaultProps: ComponentProps<typeof LeaveDialog> = {
        open: true,
        onOpenChange: vi.fn(),
        isHost: false,
        meetingId: null,
    };
    return render(<LeaveDialog {...defaultProps} {...props} />);
}

describe('LeaveDialog non-host rendering', () => {
    it('shows leave dialog title and message for non-host', () => {
        renderDialog({ isHost: false });
        expect(
            screen.getByText('Are you sure you want to leave this meeting?'),
        ).toBeInTheDocument();
        expect(
            screen.getByRole('button', { name: 'Leave' }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole('button', { name: 'Stay' }),
        ).toBeInTheDocument();
    });

    it('does not show End for All button for non-host', () => {
        renderDialog({ isHost: false });
        expect(
            screen.queryByRole('button', { name: 'End for All' }),
        ).not.toBeInTheDocument();
    });

    it('calls onOpenChange(false) when Stay is clicked for non-host', () => {
        const onOpenChange = vi.fn();
        renderDialog({ isHost: false, onOpenChange });
        act(() => {
            fireEvent.click(screen.getByRole('button', { name: 'Stay' }));
        });
        expect(onOpenChange).toHaveBeenCalledWith(false);
    });

    it('calls room.disconnect() then navigates to workspace when Leave is clicked', async () => {
        renderDialog({ isHost: false });
        act(() => {
            fireEvent.click(screen.getByRole('button', { name: 'Leave' }));
        });
        await waitFor(() => {
            expect(mockDisconnect).toHaveBeenCalledTimes(1);
            expect(mockLocationHref).toBe('/en/workspace');
        });
    });
});

describe('LeaveDialog host rendering', () => {
    it('shows host leave dialog message for host', () => {
        renderDialog({ isHost: true, meetingId: 'meeting-123' });
        expect(
            screen.getByText('Choose how you want to exit this meeting.'),
        ).toBeInTheDocument();
    });

    it('shows Leave Meeting and End for All buttons for host', () => {
        renderDialog({ isHost: true, meetingId: 'meeting-123' });
        expect(
            screen.getByRole('button', { name: 'Leave Meeting' }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole('button', { name: 'End for All' }),
        ).toBeInTheDocument();
    });

    it('shows Stay button for host', () => {
        renderDialog({ isHost: true, meetingId: 'meeting-123' });
        expect(
            screen.getByRole('button', { name: 'Stay' }),
        ).toBeInTheDocument();
    });

    it('does not show non-host single-action confirm button for host', () => {
        renderDialog({ isHost: true, meetingId: 'meeting-123' });
        expect(
            screen.queryByRole('button', { name: 'Leave' }),
        ).not.toBeInTheDocument();
    });

    it('calls room.disconnect() and navigates without calling endMeeting when Leave Meeting is clicked', async () => {
        renderDialog({ isHost: true, meetingId: 'meeting-123' });
        act(() => {
            fireEvent.click(
                screen.getByRole('button', { name: 'Leave Meeting' }),
            );
        });
        await waitFor(() => {
            expect(mockDisconnect).toHaveBeenCalledTimes(1);
            expect(mockLocationHref).toBe('/en/workspace');
        });
        expect(mockedEndMeeting).not.toHaveBeenCalled();
    });
});

describe('LeaveDialog host end-meeting submitting state', () => {
    it('disables all actions while end-meeting request is in flight', async () => {
        let resolveEndMeeting!: () => void;
        mockedEndMeeting.mockReturnValue(
            new Promise<unknown>((resolve) => {
                resolveEndMeeting = () => resolve(undefined);
            }) as ReturnType<typeof endMeeting>,
        );

        renderDialog({ isHost: true, meetingId: 'meeting-123' });

        act(() => {
            fireEvent.click(
                screen.getByRole('button', { name: 'End for All' }),
            );
        });

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'Stay' })).toBeDisabled();
            expect(
                screen.getByRole('button', { name: 'Leave Meeting' }),
            ).toBeDisabled();
            expect(screen.getByText('Ending...')).toBeInTheDocument();
        });

        resolveEndMeeting();
    });

    it('prevents duplicate end-meeting submissions while request is in flight', async () => {
        let resolveEndMeeting!: () => void;
        mockedEndMeeting.mockReturnValue(
            new Promise<unknown>((resolve) => {
                resolveEndMeeting = () => resolve(undefined);
            }) as ReturnType<typeof endMeeting>,
        );

        renderDialog({ isHost: true, meetingId: 'meeting-123' });

        act(() => {
            fireEvent.click(
                screen.getByRole('button', { name: 'End for All' }),
            );
        });

        await waitFor(() => {
            expect(screen.getByText('Ending...')).toBeInTheDocument();
        });

        act(() => {
            fireEvent.click(screen.getByText('Ending...'));
        });

        expect(mockedEndMeeting).toHaveBeenCalledTimes(1);
        resolveEndMeeting();
    });

    it('shows inline error and re-enables actions when end-meeting fails', async () => {
        mockedEndMeeting.mockRejectedValue(new Error('server error'));

        const onOpenChange = vi.fn();
        renderDialog({ isHost: true, meetingId: 'meeting-123', onOpenChange });

        act(() => {
            fireEvent.click(
                screen.getByRole('button', { name: 'End for All' }),
            );
        });

        await waitFor(() => {
            expect(
                screen.getByText(
                    'Failed to end the meeting. Please try again.',
                ),
            ).toBeInTheDocument();
        });

        expect(
            screen.getByRole('button', { name: 'End for All' }),
        ).not.toBeDisabled();
        expect(
            screen.getByRole('button', { name: 'Leave Meeting' }),
        ).not.toBeDisabled();
        expect(onOpenChange).not.toHaveBeenCalledWith(false);
    });

    it('calls endMeeting then room.disconnect then navigates when End for All succeeds', async () => {
        mockedEndMeeting.mockResolvedValue(
            undefined as unknown as ReturnType<typeof endMeeting>,
        );

        renderDialog({ isHost: true, meetingId: 'meeting-123' });

        act(() => {
            fireEvent.click(
                screen.getByRole('button', { name: 'End for All' }),
            );
        });

        await waitFor(() => {
            expect(mockedEndMeeting).toHaveBeenCalledWith({
                path: { id: 'meeting-123' },
            });
            expect(mockDisconnect).toHaveBeenCalledTimes(1);
            expect(mockLocationHref).toBe('/en/workspace');
        });
    });
});
