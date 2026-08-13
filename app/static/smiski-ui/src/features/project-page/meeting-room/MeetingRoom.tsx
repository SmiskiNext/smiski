import { useEffect, useState } from 'react';
import {
    EmptyState,
    ErrorState,
    JoinRequestToasts,
    LoadingState,
    MeetingSettingsModal,
    ParticipantPresenceToasts,
} from '../../../components/shared';
import { Button, Icon } from '../../../components/ui';
import { useCurrentUser } from '../../../context/CurrentUserContext';
import {
    type LayoutMode,
    type Participant,
    reconcilePinnedAccountId,
} from '../../../domain';
import { useJoinRequestNotifications } from '../../../hooks/useJoinRequestNotifications';
import {
    useAcceptJoinRequests,
    useDeclineJoinRequests,
    usePendingJoinRequests,
} from '../../../hooks/useJoinRequests';
import { useLiveKitRoom } from '../../../hooks/useLiveKitRoom';
import { useMeeting } from '../../../hooks/useMeeting';
import { useMeetingParticipants } from '../../../hooks/useMeetingParticipants';
import { useParticipantPresenceNotifications } from '../../../hooks/useParticipantPresenceNotifications';
import { useRoomToken } from '../../../hooks/useRoomToken';
import {
    readMeetingLayoutMode,
    writeMeetingLayoutMode,
} from '../../../utils/meetingLayoutPreference';
import { MeetingRoomShell } from './MeetingRoomShell';
import { ParticipantListPlaceholder } from './ParticipantListPlaceholder';
import { PendingJoinRequestsPanel } from './PendingJoinRequestsPanel';

const MEETING_START_FALLBACK_POLL_INTERVAL_MS = 60000;
const MEETING_START_SYNC_POLL_INTERVAL_MS = 2000;

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
 * A scheduled meeting gets a waiting room until either the backend reports it
 * RUNNING or LiveKit confirms this client connected. LiveKit is the immediate
 * source of truth during the webhook propagation window; a short poll then
 * reconciles backend state without holding the host behind a stale status.
 */
export function MeetingRoom({ meetingId, onLeave }: MeetingRoomProps) {
    const currentUser = useCurrentUser();
    const { meeting, loading, error, refetch } = useMeeting(meetingId, {
        pollWhileScheduledMs: MEETING_START_FALLBACK_POLL_INTERVAL_MS,
    });
    const { participants, loading: participantsLoading } =
        useMeetingParticipants(meetingId, meeting?.projectKey);
    const [isPeoplePanelOpen, setPeoplePanelOpen] = useState(false);
    const [isPendingPanelOpen, setPendingPanelOpen] = useState(false);
    const [isSettingsOpen, setSettingsOpen] = useState(false);
    const [layoutMode, setLayoutMode] = useState<LayoutMode>(
        readMeetingLayoutMode,
    );
    // Session-only, unlike the layout mode: a pin names one participant in one
    // call, so restoring it into a later meeting they are not in would only
    // resolve straight back to unpinned.
    const [pinnedAccountId, setPinnedAccountId] = useState<string | null>(null);

    const handleLayoutModeChange = (mode: LayoutMode) => {
        setLayoutMode(mode);
        writeMeetingLayoutMode(mode);
    };

    const handleTogglePin = (accountId: string) => {
        setPinnedAccountId((current) =>
            current === accountId ? null : accountId,
        );
    };

    const hasStarted = meeting?.status === 'RUNNING';
    const isTerminal =
        meeting?.status === 'COMPLETED' || meeting?.status === 'CANCELED';

    const {
        toasts: presenceToasts,
        enabled: notificationsEnabled,
        setEnabled: setNotificationsEnabled,
        enqueue: enqueuePresenceToast,
        dismiss: dismissPresenceToast,
    } = useParticipantPresenceNotifications();
    const {
        toasts: joinRequestToasts,
        enqueue: enqueueJoinRequestToast,
        dismissByRequestId,
    } = useJoinRequestNotifications();
    const isHost = meeting?.hostId === currentUser.accountId;
    const pending = usePendingJoinRequests(meetingId, {
        enabled: Boolean(isHost),
        onNewJoinRequest: enqueueJoinRequestToast,
    });
    const acceptJoinRequests = useAcceptJoinRequests();
    const declineJoinRequests = useDeclineJoinRequests();
    const isDecidingJoinRequest =
        acceptJoinRequests.isPending || declineJoinRequests.isPending;
    const refetchPending = pending.refetch;

    useEffect(() => {
        if (!isHost || !isPendingPanelOpen) return;
        void refetchPending();
    }, [isHost, isPendingPanelOpen, refetchPending]);

    const decideJoinRequest = (
        requestId: string,
        decide: typeof acceptJoinRequests.mutate,
    ) => {
        decide(
            { meetingId, requestIds: [requestId] },
            { onSuccess: () => dismissByRequestId(requestId) },
        );
    };

    const handleAcceptJoinRequest = (requestId: string) => {
        decideJoinRequest(requestId, acceptJoinRequests.mutate);
    };

    const handleDeclineJoinRequest = (requestId: string) => {
        decideJoinRequest(requestId, declineJoinRequests.mutate);
    };

    const pendingRequests = pending.data?.requests ?? [];
    const pendingCount = pending.data?.total ?? pendingRequests.length;

    const {
        token,
        url,
        loading: roomTokenLoading,
        waitingForApproval,
        error: roomTokenError,
    } = useRoomToken(meetingId, hasStarted);
    const liveKit = useLiveKitRoom({
        token,
        url,
        enabled: true,
        onParticipantPresence: enqueuePresenceToast,
    });
    const liveKitError = roomTokenError ?? liveKit.error;
    const isLiveKitConnected = liveKit.connectionState === 'connected';
    const isSynchronizingStatus = !hasStarted && isLiveKitConnected;
    const canRenderRoom = hasStarted || isLiveKitConnected;

    useEffect(() => {
        if (!isSynchronizingStatus) return;
        void refetch();
        const interval = window.setInterval(() => {
            void refetch();
        }, MEETING_START_SYNC_POLL_INTERVAL_MS);
        return () => window.clearInterval(interval);
    }, [isSynchronizingStatus, refetch]);

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

    if (!canRenderRoom)
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

    // Reconciled on the way down rather than in an effect: the early returns
    // above rule out another hook here, and a pinned participant who has left
    // must stop reading as pinned in the same render that drops them from the
    // roster — not one render later.
    const effectivePinnedAccountId = reconcilePinnedAccountId(
        pinnedAccountId,
        roomParticipants,
    );

    const selfAccountId = liveKit.localAccountId ?? currentUser.accountId;

    const handleLeave = async () => {
        await liveKit.leave();
        onLeave();
    };

    return (
        <div className='w-full px-4 py-4 sm:px-6'>
            {isSynchronizingStatus && (
                <div
                    role='status'
                    className='mb-3 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-200'
                >
                    Connected. Synchronizing meeting status…
                </div>
            )}
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
                        layoutMode={layoutMode}
                        onLayoutModeChange={handleLayoutModeChange}
                        pinnedAccountId={effectivePinnedAccountId}
                        onTogglePin={handleTogglePin}
                        activeSpeakerId={liveKit.activeSpeakerId}
                        mediaNotice={liveKit.mediaNotice}
                        connectionState={liveKit.connectionState}
                        onOpenSettings={() => setSettingsOpen(true)}
                        pendingJoinRequestCount={
                            isHost ? pendingCount : undefined
                        }
                        isPendingPanelOpen={isPendingPanelOpen}
                        onTogglePendingPanel={
                            isHost
                                ? () => setPendingPanelOpen((value) => !value)
                                : undefined
                        }
                    />
                </div>
                {isPendingPanelOpen && isHost && (
                    <PendingJoinRequestsPanel
                        requests={pendingRequests}
                        total={pendingCount}
                        isLoading={pending.isLoading}
                        isDeciding={isDecidingJoinRequest}
                        onAccept={handleAcceptJoinRequest}
                        onDecline={handleDeclineJoinRequest}
                        onClose={() => setPendingPanelOpen(false)}
                    />
                )}
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
                showNotificationPreferences
                isHost={isHost}
                notificationsEnabled={notificationsEnabled}
                onNotificationsEnabledChange={setNotificationsEnabled}
            />
            {isHost && (
                <div className='pointer-events-none fixed right-4 bottom-4 z-50 flex w-80 flex-col gap-2'>
                    <JoinRequestToasts
                        toasts={joinRequestToasts}
                        isDeciding={isDecidingJoinRequest}
                        onAccept={handleAcceptJoinRequest}
                        onDecline={handleDeclineJoinRequest}
                        onOpenPanel={() => setPendingPanelOpen(true)}
                    />
                    <ParticipantPresenceToasts
                        toasts={presenceToasts}
                        onDismiss={dismissPresenceToast}
                    />
                </div>
            )}
            {!isHost && (
                <div className='pointer-events-none fixed right-4 bottom-4 z-50 w-80'>
                    <ParticipantPresenceToasts
                        toasts={presenceToasts}
                        onDismiss={dismissPresenceToast}
                    />
                </div>
            )}
        </div>
    );
}
