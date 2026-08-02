import { useState } from 'react';
import { ErrorState, LoadingState } from '../../../components/shared';
import { Button, Icon } from '../../../components/ui';
import { useCurrentUser } from '../../../context/CurrentUserContext';
import type { Participant } from '../../../domain';
import { useLiveKitRoom } from '../../../hooks/useLiveKitRoom';
import { useMeeting } from '../../../hooks/useMeeting';
import { useMeetingParticipants } from '../../../hooks/useMeetingParticipants';
import { useRoomToken } from '../../../hooks/useRoomToken';
import { MeetingRoomShell } from './MeetingRoomShell';
import { ParticipantListPlaceholder } from './ParticipantListPlaceholder';

export interface MeetingRoomProps {
    meetingId: string;
    onLeave: () => void;
}

// Standalone `pnpm ui:dev` has no Forge bridge to reach `getRoomToken`
// through, so LiveKit is fully disabled there — see useRoomToken.ts /
// MeetingRoomShell.tsx's local-state fallback for how the room still renders.
const isLiveKitEnabled = !import.meta.env.DEV;

export function MeetingRoom({ meetingId, onLeave }: MeetingRoomProps) {
    const currentUser = useCurrentUser();
    const { meeting, loading } = useMeeting(meetingId);
    const { participants, loading: participantsLoading } =
        useMeetingParticipants(meetingId, meeting?.projectKey);
    const [isPeoplePanelOpen, setPeoplePanelOpen] = useState(false);

    const {
        token,
        url,
        error: roomTokenError,
    } = useRoomToken(meetingId, isLiveKitEnabled);
    const liveKit = useLiveKitRoom({ token, url, enabled: isLiveKitEnabled });
    const liveKitError = roomTokenError ?? liveKit.error;

    if (loading)
        return (
            <div className='p-6'>
                <LoadingState label='Loading meeting room…' />
            </div>
        );

    // LiveKit's real roster (once connected) takes priority over the mocked
    // meeting roster, which in turn beats the self-only placeholder — same
    // three-tier fallback as before LiveKit existed, just with a new top tier.
    const roomParticipants: (
        | Participant
        | (typeof liveKit.participants)[number]
    )[] =
        isLiveKitEnabled && liveKit.participants.length
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
            {isLiveKitEnabled && liveKitError && (
                <div className='mb-3'>
                    <ErrorState
                        title="Couldn't connect to the video call"
                        message={`${liveKitError.message} — showing the room without live video.`}
                    />
                </div>
            )}
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
                        isMicOn={isLiveKitEnabled ? liveKit.isMicOn : undefined}
                        isCameraOn={
                            isLiveKitEnabled ? liveKit.isCameraOn : undefined
                        }
                        isScreenSharing={
                            isLiveKitEnabled
                                ? liveKit.isScreenSharing
                                : undefined
                        }
                        screenShare={
                            isLiveKitEnabled ? liveKit.screenShare : null
                        }
                        onToggleMic={
                            isLiveKitEnabled ? liveKit.toggleMic : undefined
                        }
                        onToggleCamera={
                            isLiveKitEnabled ? liveKit.toggleCamera : undefined
                        }
                        onToggleScreenShare={
                            isLiveKitEnabled
                                ? liveKit.toggleScreenShare
                                : undefined
                        }
                        connectionState={
                            isLiveKitEnabled
                                ? liveKit.connectionState
                                : undefined
                        }
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
        </div>
    );
}
