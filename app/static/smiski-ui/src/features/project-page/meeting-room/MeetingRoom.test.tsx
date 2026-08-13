// @vitest-environment jsdom

import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Meeting, MeetingStatus } from '../../../domain';
import type { UseLiveKitRoomResult } from '../../../hooks/useLiveKitRoom';
import type { UseRoomTokenResult } from '../../../hooks/useRoomToken';

const hooks = vi.hoisted(() => ({
    useMeeting: vi.fn(),
    useRoomToken: vi.fn(),
    useLiveKitRoom: vi.fn(),
}));

vi.mock('../../../hooks/useMeeting', () => ({ useMeeting: hooks.useMeeting }));

vi.mock('../../../hooks/useRoomToken', () => ({
    useRoomToken: hooks.useRoomToken,
}));

vi.mock('../../../hooks/useLiveKitRoom', () => ({
    useLiveKitRoom: hooks.useLiveKitRoom,
}));

vi.mock('../../../hooks/useMeetingParticipants', () => ({
    useMeetingParticipants: () => ({
        participants: [],
        loading: false,
        error: null,
    }),
}));

vi.mock('../../../context/CurrentUserContext', () => ({
    useCurrentUser: () => ({
        accountId: 'account-self',
        displayName: 'Self User',
    }),
}));

// Keeps the real state components (the assertions target their output) while
// dropping `MeetingSettingsModal`, the one barrel export that pulls in Ant
// Design and is never reachable from the states under test.
// `ParticipantPresenceToasts` is stubbed for the same reason its modal sibling
// is: the room renders it unconditionally, and the presence toasts are covered
// by their own test.
vi.mock('../../../components/shared', async () => {
    const { EmptyState } = await import(
        '../../../components/shared/EmptyState'
    );
    const { ErrorState } = await import(
        '../../../components/shared/ErrorState'
    );
    const { LoadingState } = await import(
        '../../../components/shared/LoadingState'
    );
    return {
        EmptyState,
        ErrorState,
        LoadingState,
        MeetingSettingsModal: () => null,
        ParticipantPresenceToasts: () => null,
        JoinRequestToasts: () => null,
    };
});

vi.mock('./MeetingRoomShell', async () => {
    const { createElement } = await import('react');
    return {
        MeetingRoomShell: () =>
            createElement('div', { 'data-testid': 'meeting-room-shell' }),
    };
});

vi.mock('../../../hooks/useJoinRequests', () => ({
    usePendingJoinRequests: () => ({
        data: { requests: [], total: 0, offset: 0, pageSize: 20 },
        isLoading: false,
        refetch: vi.fn(),
    }),
    useAcceptJoinRequests: () => ({ isPending: false, mutate: vi.fn() }),
    useDeclineJoinRequests: () => ({ isPending: false, mutate: vi.fn() }),
}));

vi.mock('../../../hooks/useJoinRequestNotifications', () => ({
    useJoinRequestNotifications: () => ({
        toasts: [],
        enqueue: vi.fn(),
        dismiss: vi.fn(),
        dismissByRequestId: vi.fn(),
    }),
}));

import { MeetingRoom } from './MeetingRoom';

const MEETING_ID = 'meeting-1';
const WAITING_TO_START_LABEL = 'Waiting for the meeting to start…';
const refetchMeeting = vi.fn();

function meeting(status: MeetingStatus): Meeting {
    return {
        id: MEETING_ID,
        title: 'Room gating test',
        projectId: 'project-1',
        projectKey: 'TEST',
        issueId: 'issue-1',
        issueKey: 'TEST-1',
        creatorId: 'account-host',
        creatorName: 'Host',
        hostId: 'account-host',
        hostName: 'Host',
        status,
        participantCount: 0,
    };
}

const IDLE_LIVEKIT: UseLiveKitRoomResult = {
    connectionState: 'idle',
    error: null,
    localAccountId: null,
    participants: [],
    screenShare: null,
    isMicOn: false,
    isCameraOn: false,
    isScreenSharing: false,
    activeSpeakerId: null,
    mediaNotice: null,
    toggleMic: vi.fn(),
    toggleCamera: vi.fn(),
    toggleScreenShare: vi.fn(),
    leave: vi.fn(),
};

const NO_TOKEN: UseRoomTokenResult = {
    token: null,
    url: null,
    loading: false,
    waitingForApproval: false,
    error: null,
};

function renderRoom(status: MeetingStatus | null, error: Error | null = null) {
    hooks.useMeeting.mockReturnValue({
        meeting: status ? meeting(status) : null,
        loading: false,
        error,
        refetch: refetchMeeting,
    });
    const onLeave = vi.fn();
    render(<MeetingRoom meetingId={MEETING_ID} onLeave={onLeave} />);
    return { onLeave };
}

describe('MeetingRoom start gating', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        hooks.useRoomToken.mockReturnValue(NO_TOKEN);
        hooks.useLiveKitRoom.mockReturnValue(IDLE_LIVEKIT);
        refetchMeeting.mockResolvedValue(undefined);
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it('holds the room token back and waits while the meeting is scheduled', () => {
        renderRoom('SCHEDULED');

        expect(screen.getByText(WAITING_TO_START_LABEL)).toBeDefined();
        expect(hooks.useRoomToken).toHaveBeenCalledWith(MEETING_ID, false);
    });

    it('reports a completed meeting instead of entering the room', () => {
        renderRoom('COMPLETED');

        expect(screen.getByText('This meeting has ended')).toBeDefined();
        expect(screen.queryByText(WAITING_TO_START_LABEL)).toBeNull();
    });

    it('reports a canceled meeting instead of entering the room', () => {
        renderRoom('CANCELED');

        expect(screen.getByText('This meeting was canceled')).toBeDefined();
        expect(screen.queryByText(WAITING_TO_START_LABEL)).toBeNull();
    });

    it('surfaces a failed meeting detail load', () => {
        renderRoom(null, new Error('Meeting not found'));

        expect(screen.getByText("Couldn't load the meeting")).toBeDefined();
        expect(screen.getByText('Meeting not found')).toBeDefined();
    });

    it('requests the token and renders the room once the meeting is running', () => {
        hooks.useRoomToken.mockReturnValue({
            ...NO_TOKEN,
            token: 'room-token',
            url: 'wss://livekit.test',
        });
        renderRoom('RUNNING');

        expect(screen.getByTestId('meeting-room-shell')).toBeDefined();
        expect(screen.queryByText(WAITING_TO_START_LABEL)).toBeNull();
        expect(hooks.useRoomToken).toHaveBeenCalledWith(MEETING_ID, true);
    });

    it('renders immediately and refreshes backend state when LiveKit connects first', () => {
        hooks.useRoomToken.mockReturnValue({
            ...NO_TOKEN,
            token: 'room-token',
            url: 'wss://livekit.test',
        });
        hooks.useLiveKitRoom.mockReturnValue({
            ...IDLE_LIVEKIT,
            connectionState: 'connected',
        });

        renderRoom('SCHEDULED');

        expect(screen.getByTestId('meeting-room-shell')).toBeDefined();
        expect(
            screen.getByText('Connected. Synchronizing meeting status…'),
        ).toBeDefined();
        expect(screen.queryByText(WAITING_TO_START_LABEL)).toBeNull();
        expect(refetchMeeting).toHaveBeenCalledTimes(1);
        expect(hooks.useRoomToken).toHaveBeenCalledWith(MEETING_ID, false);
    });

    it('stops the short sync poll after the backend reports RUNNING', async () => {
        vi.useFakeTimers();
        let status: MeetingStatus = 'SCHEDULED';
        hooks.useMeeting.mockImplementation(() => ({
            meeting: meeting(status),
            loading: false,
            error: null,
            refetch: refetchMeeting,
        }));
        hooks.useRoomToken.mockReturnValue({
            ...NO_TOKEN,
            token: 'room-token',
            url: 'wss://livekit.test',
        });
        hooks.useLiveKitRoom.mockReturnValue({
            ...IDLE_LIVEKIT,
            connectionState: 'connected',
        });

        const onLeave = vi.fn();
        const { rerender } = render(
            <MeetingRoom meetingId={MEETING_ID} onLeave={onLeave} />,
        );
        expect(refetchMeeting).toHaveBeenCalledTimes(1);

        await act(() => vi.advanceTimersByTimeAsync(2000));
        expect(refetchMeeting).toHaveBeenCalledTimes(2);

        status = 'RUNNING';
        rerender(<MeetingRoom meetingId={MEETING_ID} onLeave={onLeave} />);
        await act(() => vi.advanceTimersByTimeAsync(4000));

        expect(refetchMeeting).toHaveBeenCalledTimes(2);
        expect(
            screen.queryByText('Connected. Synchronizing meeting status…'),
        ).toBeNull();
    });
});
