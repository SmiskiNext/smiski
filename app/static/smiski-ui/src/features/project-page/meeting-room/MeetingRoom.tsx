import { useState } from 'react';
import {
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
 */
export function MeetingRoom({ meetingId, onLeave }: MeetingRoomProps) {
    const currentUser = useCurrentUser();
    const { meeting, loading } = useMeeting(meetingId);
    const { participants, loading: participantsLoading } =
        useMeetingParticipants(meetingId, meeting?.projectKey);
    const [isPeoplePanelOpen, setPeoplePanelOpen] = useState(false);
    const [isSettingsOpen, setSettingsOpen] = useState(false);

    const {
        token,
        url,
        loading: roomTokenLoading,
        waitingForApproval,
        error: roomTokenError,
    } = useRoomToken(meetingId);
    const liveKit = useLiveKitRoom({ token, url, enabled: true });
    const liveKitError = roomTokenError ?? liveKit.error;

    if (loading)
        return (
            <div className='p-6'>
                <LoadingState label='Loading meeting room…' />
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
