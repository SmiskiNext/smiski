import { useState } from 'react';
import {
    EmptyState,
    ErrorState,
    LoadingState,
    MeetingSettingsModal,
} from '../../../components/shared';
import { Button, Icon } from '../../../components/ui';
import { useCurrentUser } from '../../../context/CurrentUserContext';
import type { Participant } from '../../../domain';
import { useLiveKitRoom } from '../../../hooks/useLiveKitRoom';
import { useMeeting } from '../../../hooks/useMeeting';
import { useMeetingParticipants } from '../../../hooks/useMeetingParticipants';
import { useRoomToken } from '../../../hooks/useRoomToken';
import { MeetingRoomShell } from './MeetingRoomShell';
import { ParticipantListPlaceholder } from './ParticipantListPlaceholder';
import { PendingJoinRequestsPanel } from './PendingJoinRequestsPanel';

const MEETING_START_POLL_INTERVAL_MS = 5000;

export interface MeetingRoomProps {
    meetingId: string;
    onLeave: () => void;
}

/**
 * Live meeting room for the project page surface: joins the LiveKit room
 * behind `meetingId` and renders it through `MeetingRoomShell`.
 *
 * The displayed roster follows a three-tier precedence — LiveKit's real-time
 * roster wins once connected, the backend meeting roster comes next, and a
 * self-only placeholder covers the window before either has loaded.
 *
 * A meeting that has not started yet gets a waiting room rather than the room
 * shell, and its room-token request stays held back until then: the backend
 * `join` operation does not gate on meeting status, so asking early would
 * either drop the user into an empty room or raise a premature host-approval
 * request. Polling the meeting detail swaps the waiting room for the real one
 * once the host starts it.
 */
export function MeetingRoom({ meetingId, onLeave }: MeetingRoomProps) {
    const currentUser = useCurrentUser();
    const { meeting, loading, error } = useMeeting(meetingId, {
        pollWhileScheduledMs: MEETING_START_POLL_INTERVAL_MS,
    });
    const { participants, loading: participantsLoading } =
        useMeetingParticipants(meetingId, meeting?.projectKey);
    const [isPeoplePanelOpen, setPeoplePanelOpen] = useState(false);
    const [isSettingsOpen, setSettingsOpen] = useState(false);

    const hasStarted = meeting?.status === 'RUNNING';
    const isTerminal =
        meeting?.status === 'COMPLETED' || meeting?.status === 'CANCELED';

    const {
        token,
        url,
        loading: roomTokenLoading,
        waitingForApproval,
        error: roomTokenError,
    } = useRoomToken(meetingId, hasStarted);
    const liveKit = useLiveKitRoom({ token, url, enabled: true });
    const liveKitError = roomTokenError ?? liveKit.error;

    if (loading)
        return (
            <div className='p-6'>
                <LoadingState label='Loading meeting room…' />
            </div>
        );

    if (!meeting)
        return (
            <div className='p-6'>
                <ErrorState
                    title="Couldn't load the meeting"
                    message={error?.message}
                />
                <div className='mt-4 text-center'>
                    <Button size='sm' variant='ghost' onClick={onLeave}>
                        Back to meetings
                    </Button>
                </div>
            </div>
        );

    if (isTerminal)
        return (
            <div className='p-6'>
                <EmptyState
                    header={
                        meeting.status === 'CANCELED'
                            ? 'This meeting was canceled'
                            : 'This meeting has ended'
                    }
                    description={
                        meeting.status === 'CANCELED'
                            ? 'The host canceled this meeting.'
                            : 'This meeting has already ended.'
                    }
                    primaryAction={
                        <Button size='sm' variant='secondary' onClick={onLeave}>
                            Back to meetings
                        </Button>
                    }
                />
            </div>
        );

    if (!hasStarted)
        return (
            <div className='p-6'>
                <LoadingState label='Waiting for the meeting to start…' />
                <div className='mt-4 text-center'>
                    <Button size='sm' variant='ghost' onClick={onLeave}>
                        Leave
                    </Button>
                </div>
            </div>
        );

    if (waitingForApproval)
        return (
            <div className='p-6'>
                <LoadingState label='Waiting for the host to approve your join request…' />
                <div className='mt-4 text-center'>
                    <Button size='sm' variant='ghost' onClick={onLeave}>
                        Leave waiting room
                    </Button>
                </div>
            </div>
        );

    if (roomTokenLoading)
        return (
            <div className='p-6'>
                <LoadingState label='Requesting access to the meeting…' />
            </div>
        );

    if (roomTokenError && !token)
        return (
            <div className='p-6'>
                <ErrorState
                    title="Couldn't join the meeting"
                    message={roomTokenError.message}
                />
                <div className='mt-4 text-center'>
                    <Button size='sm' variant='ghost' onClick={onLeave}>
                        Back to meetings
                    </Button>
                </div>
            </div>
        );

    const roomParticipants: (
        | Participant
        | (typeof liveKit.participants)[number]
    )[] = liveKit.participants.length
        ? liveKit.participants
        : participants.length
          ? participants
          : [
                {
                    accountId: currentUser.accountId,
                    displayName: currentUser.displayName,
                    role: 'HOST',
                },
            ];

    const selfAccountId = liveKit.localAccountId ?? currentUser.accountId;
    const isHost = meeting?.hostId === currentUser.accountId;

    const handleLeave = async () => {
        await liveKit.leave();
        onLeave();
    };

    return (
        <div className='w-full px-4 py-4 sm:px-6'>
            <div className='mb-3 flex items-center justify-between'>
                <Button
                    size='sm'
                    variant='ghost'
                    className='rounded-md'
                    leadingIcon={
                        <Icon
                            name='chevronDown'
                            size={15}
                            className='rotate-90'
                        />
                    }
                    onClick={handleLeave}
                >
                    Back to meetings
                </Button>
                <span className='text-xs text-[var(--text-faint)]'>
                    {meeting?.issueKey ?? meeting?.projectKey}
                </span>
            </div>
            {liveKitError && (
                <div className='mb-3'>
                    <ErrorState
                        title="Couldn't connect to the video call"
                        message={`${liveKitError.message} — showing the room without live video.`}
                    />
                </div>
            )}
            {isHost && <PendingJoinRequestsPanel meetingId={meetingId} />}
            <div className='flex flex-col gap-4 xl:flex-row'>
                <div className='min-w-0 flex-1'>
                    <MeetingRoomShell
                        meeting={meeting}
                        participants={roomParticipants}
                        selfAccountId={selfAccountId}
                        isPeoplePanelOpen={isPeoplePanelOpen}
                        onTogglePeoplePanel={() =>
                            setPeoplePanelOpen((value) => !value)
                        }
                        onLeave={handleLeave}
                        isMicOn={liveKit.isMicOn}
                        isCameraOn={liveKit.isCameraOn}
                        isScreenSharing={liveKit.isScreenSharing}
                        screenShare={liveKit.screenShare}
                        onToggleMic={liveKit.toggleMic}
                        onToggleCamera={liveKit.toggleCamera}
                        onToggleScreenShare={liveKit.toggleScreenShare}
                        mediaNotice={liveKit.mediaNotice}
                        connectionState={liveKit.connectionState}
                        onOpenSettings={() => setSettingsOpen(true)}
                    />
                </div>
                {isPeoplePanelOpen && (
                    <ParticipantListPlaceholder
                        participants={
                            participants.length
                                ? participants
                                : roomParticipants
                        }
                        loading={participantsLoading}
                    />
                )}
            </div>
            <MeetingSettingsModal
                isOpen={isSettingsOpen}
                meetingId={meetingId}
                onClose={() => setSettingsOpen(false)}
                onSaved={() => setSettingsOpen(false)}
            />
        </div>
    );
}
