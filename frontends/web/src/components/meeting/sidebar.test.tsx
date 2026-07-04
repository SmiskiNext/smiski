import { act, fireEvent, render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { MeetingSidebar } from './sidebar.tsx';
import type { ParticipantViewModel } from './types.ts';

const TRANSLATIONS: Record<string, string> = {
    tabPeople: 'People',
    tabChat: 'Chat',
    you: 'You',
    host: 'Host',
    meetingDetails: 'Meeting Details',
    closeSidebar: 'Close panel',
    activeSession: 'Active session',
    moderationMuteAll: 'Mute All Microphones',
    moderationMuteAllLoading: 'Muting All...',
    moderationMuteAllSuccess: 'All Muted',
    moderationMuteAllError:
        'Failed to mute all participants. Please try again.',
    moderationMuteMic: 'Mute {name} microphone',
    moderationMuteCamera: 'Mute {name} camera',
    moderationMuteMicTooltip: 'Mute microphone',
    moderationMuteCameraTooltip: 'Mute camera',
    moderationMuteError: 'Failed to mute participant. Please try again.',
    participantSearch: 'Search participants...',
    waitingRoomTitle: 'Waiting Room',
    waitingRoomApproveAll: 'Approve All',
};

vi.mock('next-intl', () => ({
    useTranslations:
        () => (key: string, values?: Record<string, string | number>) => {
            const template = key in TRANSLATIONS ? TRANSLATIONS[key] : key;
            if (!values) return template;
            return Object.entries(values).reduce(
                (acc, [k, v]) => acc.replace(`{${k}}`, String(v)),
                template,
            );
        },
}));

function makeParticipant(
    overrides: Partial<ParticipantViewModel> = {},
): ParticipantViewModel {
    return {
        identity: 'user-1',
        displayName: 'Alice Smith',
        isMicEnabled: true,
        isCameraEnabled: true,
        isLocal: false,
        livekitParticipant: {} as ParticipantViewModel['livekitParticipant'],
        ...overrides,
    };
}

function renderSidebar(
    props: Partial<ComponentProps<typeof MeetingSidebar>> = {},
) {
    const defaultProps: ComponentProps<typeof MeetingSidebar> = {
        participants: [makeParticipant()],
        messages: [],
        loading: false,
        error: false,
        sendError: false,
        isOpen: true,
        isHost: false,
        activeTab: 'chat',
        meetingId: null,
        userId: 'viewer-1',
        onClose: vi.fn(),
        onTabChange: vi.fn(),
        onApprove: vi.fn(),
        onDeny: vi.fn(),
        onApproveAll: vi.fn(),
        onSendMessage: vi.fn(),
        onRetry: vi.fn(),
        onLoadHistory: vi.fn(),
        onMuteAll: vi.fn().mockResolvedValue(undefined),
        onMuteMic: vi.fn().mockResolvedValue(undefined),
        onMuteCamera: vi.fn().mockResolvedValue(undefined),
        hasWaitingRoom: false,
        waitingCount: 0,
        waitingRequests: [],
    };
    return render(<MeetingSidebar {...defaultProps} {...props} />);
}

function openPeopleTab() {
    act(() => {
        fireEvent.click(screen.getByRole('button', { name: 'People' }));
    });
}

describe('MeetingSidebar People tab moderation', () => {
    describe('host vs non-host rendering', () => {
        it('hides mute controls for non-host users', () => {
            renderSidebar({ isHost: false });
            openPeopleTab();
            expect(
                screen.queryByTitle('Mute microphone'),
            ).not.toBeInTheDocument();
        });

        it('shows mute controls for host users on moderable rows', () => {
            renderSidebar({ isHost: true, meetingId: 'meeting-123' });
            openPeopleTab();
            expect(screen.getByTitle('Mute microphone')).toBeInTheDocument();
            expect(screen.getByTitle('Mute camera')).toBeInTheDocument();
        });

        it('hides mute controls for the local participant row when host', () => {
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                participants: [makeParticipant({ isLocal: true })],
            });
            openPeopleTab();
            expect(
                screen.queryByTitle('Mute microphone'),
            ).not.toBeInTheDocument();
        });

        it('hides mute controls for participant rows marked with role HOST', () => {
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                participants: [makeParticipant({ role: 'HOST' })],
            });
            openPeopleTab();
            expect(
                screen.queryByTitle('Mute microphone'),
            ).not.toBeInTheDocument();
        });

        it('shows mute controls only for remote non-host participant rows', () => {
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                participants: [
                    makeParticipant({ identity: 'host-1', role: 'HOST' }),
                    makeParticipant({
                        identity: 'remote-1',
                        role: 'PARTICIPANT',
                    }),
                ],
            });
            openPeopleTab();
            expect(screen.getAllByTitle('Mute microphone')).toHaveLength(1);
        });
    });

    describe('mute-all banner', () => {
        it('does not render the mute-all banner for non-host users', () => {
            renderSidebar({ isHost: false });
            openPeopleTab();
            expect(
                screen.queryByRole('button', { name: 'Mute All Microphones' }),
            ).not.toBeInTheDocument();
        });

        it('renders the mute-all banner for host users with a meetingId', () => {
            renderSidebar({ isHost: true, meetingId: 'meeting-123' });
            openPeopleTab();
            expect(
                screen.getByRole('button', { name: 'Mute All Microphones' }),
            ).toBeInTheDocument();
        });

        it('does not render the mute-all banner when meetingId is null even for the host', () => {
            renderSidebar({ isHost: true, meetingId: null });
            openPeopleTab();
            expect(
                screen.queryByRole('button', { name: 'Mute All Microphones' }),
            ).not.toBeInTheDocument();
        });
    });

    describe('mute action callbacks', () => {
        it('calls onMuteMic with the participant identity when the mic button is clicked', async () => {
            const onMuteMic = vi.fn().mockResolvedValue(undefined);
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                participants: [
                    makeParticipant({
                        identity: 'alice-123',
                        displayName: 'Alice',
                    }),
                ],
                onMuteMic,
            });
            openPeopleTab();
            await act(async () => {
                fireEvent.click(screen.getByTitle('Mute microphone'));
            });
            expect(onMuteMic).toHaveBeenCalledWith('alice-123');
        });

        it('calls onMuteCamera with the participant identity when the camera button is clicked', async () => {
            const onMuteCamera = vi.fn().mockResolvedValue(undefined);
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                participants: [
                    makeParticipant({
                        identity: 'bob-456',
                        displayName: 'Bob',
                    }),
                ],
                onMuteCamera,
            });
            openPeopleTab();
            await act(async () => {
                fireEvent.click(screen.getByTitle('Mute camera'));
            });
            expect(onMuteCamera).toHaveBeenCalledWith('bob-456');
        });

        it('surfaces an error message when onMuteMic throws', async () => {
            const onMuteMic = vi.fn().mockRejectedValue(new Error('fail'));
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                onMuteMic,
            });
            openPeopleTab();
            await act(async () => {
                fireEvent.click(screen.getByTitle('Mute microphone'));
            });
            expect(screen.getByRole('alert')).toHaveTextContent(
                'Failed to mute participant. Please try again.',
            );
        });

        it('surfaces an error message when onMuteAll throws', async () => {
            const onMuteAll = vi.fn().mockRejectedValue(new Error('fail'));
            renderSidebar({
                isHost: true,
                meetingId: 'meeting-123',
                onMuteAll,
            });
            openPeopleTab();
            await act(async () => {
                fireEvent.click(
                    screen.getByRole('button', {
                        name: 'Mute All Microphones',
                    }),
                );
            });
            expect(screen.getByRole('alert')).toHaveTextContent(
                'Failed to mute all participants. Please try again.',
            );
        });
    });
});
