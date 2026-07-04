'use client';

import {
    LiveKitRoom,
    useConnectionState,
    useLocalParticipant,
    useRemoteParticipants,
    useRoomContext,
} from '@livekit/components-react';
import { ConnectionState, DisconnectReason, RoomEvent } from 'livekit-client';
import { useLocale, useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button.tsx';
import {
    getMeeting,
    muteAllParticipants,
    muteParticipantTrack,
} from '@/generated/sdk.gen.ts';
import { useCallTimer } from '@/hooks/use-call-timer.ts';
import { useMeetingChat } from '@/hooks/use-meeting-chat.ts';
import { useRecordingState } from '@/hooks/use-recording-state.ts';
import { useScreenShare } from '@/hooks/use-screen-share.ts';
import { useWaitingRoom } from '@/hooks/use-waiting-room.ts';
import {
    MEETING_AUDIO_KEY,
    MEETING_ID_KEY,
    MEETING_ROOM_KEY,
    MEETING_TOKEN_KEY,
    MEETING_VIDEO_KEY,
} from '@/lib/meeting-room-handoff.ts';
import { ADMISSION_POLICY_WAITING_ROOM } from '@/lib/schemas/meeting.ts';
import { LeaveDialog } from './leave-dialog.tsx';
import { MeetingSettingsDialog } from './meeting-settings-dialog.tsx';
import { ParticipantGrid } from './participant-grid.tsx';
import { RecordingBanner } from './recording-banner.tsx';
import { RecordingConfirmDialog } from './recording-confirm-dialog.tsx';
import { ScreenShareFullscreen } from './screen-share-fullscreen.tsx';
import { MeetingSidebar } from './sidebar.tsx';
import { RecordingOverlay, TimerOverlay } from './status-overlays.tsx';
import { MeetingToolbar } from './toolbar.tsx';
import type { ParticipantRole, ParticipantViewModel } from './types.ts';

const liveKitUrl = process.env.NEXT_PUBLIC_LIVEKIT_URL;

if (!liveKitUrl) {
    throw new Error(
        'Missing required environment variable: NEXT_PUBLIC_LIVEKIT_URL. '
            + 'Set this to the LiveKit WebSocket URL (ws:// or wss://) before starting the application. '
            + 'See frontends/web/.env.local.example for the required variables.',
    );
}

type SessionCredentials = {
    token: string;
    roomName: string;
    meetingId: string | null;
    audioEnabled: boolean;
    videoEnabled: boolean;
} | null;

function parseStoredBoolean(value: string | null, fallback: boolean): boolean {
    if (value === 'true') return true;
    if (value === 'false') return false;
    return fallback;
}

function consumeSessionCredentials(): SessionCredentials {
    if (typeof window === 'undefined') return null;
    const token = sessionStorage.getItem(MEETING_TOKEN_KEY);
    const roomName = sessionStorage.getItem(MEETING_ROOM_KEY);
    const meetingId = sessionStorage.getItem(MEETING_ID_KEY);
    const audioRaw = sessionStorage.getItem(MEETING_AUDIO_KEY);
    const videoRaw = sessionStorage.getItem(MEETING_VIDEO_KEY);
    sessionStorage.removeItem(MEETING_TOKEN_KEY);
    sessionStorage.removeItem(MEETING_ROOM_KEY);
    sessionStorage.removeItem(MEETING_ID_KEY);
    sessionStorage.removeItem(MEETING_AUDIO_KEY);
    sessionStorage.removeItem(MEETING_VIDEO_KEY);
    if (token && roomName) {
        return {
            token,
            roomName,
            meetingId,
            audioEnabled: parseStoredBoolean(audioRaw, true),
            videoEnabled: parseStoredBoolean(videoRaw, true),
        };
    }
    return null;
}

function mapConnectionStatus(
    state: ConnectionState,
): 'connected' | 'reconnecting' | 'disconnected' {
    if (
        state === ConnectionState.Reconnecting
        || state === ConnectionState.SignalReconnecting
    ) {
        return 'reconnecting';
    }
    if (state === ConnectionState.Connected) {
        return 'connected';
    }
    return 'disconnected';
}

type BannerType = 'started' | 'stopped' | null;

type MeetingRoomContentProps = {
    meetingId: string | null;
    userId: string | null;
    hostId: string | null;
    hasWaitingRoom: boolean;
    shortCode: string | null;
};

function MeetingRoomContent({
    meetingId,
    userId,
    hasWaitingRoom,
    shortCode,
}: MeetingRoomContentProps) {
    const t = useTranslations('meetingRoom');
    const locale = useLocale();

    const connectionState = useConnectionState();
    const room = useRoomContext();
    const { localParticipant } = useLocalParticipant();
    const remoteParticipants = useRemoteParticipants();

    const { formattedDuration } = useCallTimer();

    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [activeTab, setActiveTab] = useState<'chat' | 'people'>('chat');
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [leaveDialogOpen, setLeaveDialogOpen] = useState(false);
    const [recordingConfirmOpen, setRecordingConfirmOpen] = useState(false);
    const [bannerType, setBannerType] = useState<BannerType>(null);

    const isHost = resolveRole(localParticipant.attributes?.role) === 'HOST';
    const connectionStatus = mapConnectionStatus(connectionState);
    const isReconnecting = connectionStatus === 'reconnecting';

    const isChatVisible = sidebarOpen && activeTab === 'chat';

    const chat = useMeetingChat(meetingId ?? '', userId ?? '', isChatVisible);

    const waitingRoom = useWaitingRoom(
        isHost && hasWaitingRoom ? meetingId : null,
    );

    const {
        recordingState,
        error: recordingError,
        startRecording,
        stopRecording,
        clearError: clearRecordingError,
    } = useRecordingState(meetingId);

    const {
        activeSharer,
        canShareScreen,
        error: screenShareError,
        isHostSharing,
        isLocalSharing,
        toggle: toggleScreenShare,
    } = useScreenShare();

    const navigateToWorkspace = useCallback(() => {
        window.location.href = `/${locale}/workspace`;
    }, [locale]);

    const prevRecordingStateRef = useRef(recordingState);
    const isFirstRenderRef = useRef(true);

    useEffect(() => {
        function handleDisconnected(reason?: DisconnectReason) {
            if (
                reason === DisconnectReason.SERVER_SHUTDOWN
                || reason === DisconnectReason.ROOM_DELETED
            ) {
                toast.info(t('meetingEndedByHost'));
                navigateToWorkspace();
            }
        }

        room.on(RoomEvent.Disconnected, handleDisconnected);
        return () => {
            room.off(RoomEvent.Disconnected, handleDisconnected);
        };
    }, [room, t, navigateToWorkspace]);

    useEffect(() => {
        if (isFirstRenderRef.current) {
            isFirstRenderRef.current = false;
            prevRecordingStateRef.current = recordingState;
            return;
        }

        const prev = prevRecordingStateRef.current;
        prevRecordingStateRef.current = recordingState;

        if (recordingState === 'recording' && prev !== 'recording') {
            setRecordingConfirmOpen(false);
            setBannerType('started');
        } else if (prev === 'recording' && recordingState !== 'recording') {
            setBannerType('stopped');
        }
    }, [recordingState]);

    function resolveRole(role: string | undefined): ParticipantRole {
        return role === 'HOST' || role === 'GUEST' ? role : 'PARTICIPANT';
    }

    const sidebarParticipants: ParticipantViewModel[] = [
        {
            identity: localParticipant.identity,
            displayName: localParticipant.name ?? localParticipant.identity,
            avatarUrl: localParticipant.attributes?.avatarUrl,
            isMicEnabled: localParticipant.isMicrophoneEnabled,
            isCameraEnabled: localParticipant.isCameraEnabled,
            isLocal: true,
            role: resolveRole(localParticipant.attributes?.role),
            livekitParticipant: localParticipant,
        },
        ...remoteParticipants.map((p) => ({
            identity: p.identity,
            displayName: p.name ?? p.identity,
            avatarUrl: p.attributes?.avatarUrl,
            isMicEnabled: p.isMicrophoneEnabled,
            isCameraEnabled: p.isCameraEnabled,
            isLocal: false,
            role: resolveRole(p.attributes?.role),
            livekitParticipant: p,
        })),
    ];

    async function handleToggleMic() {
        await localParticipant.setMicrophoneEnabled(
            !localParticipant.isMicrophoneEnabled,
        );
    }

    async function handleToggleVideo() {
        await localParticipant.setCameraEnabled(
            !localParticipant.isCameraEnabled,
        );
    }

    const handleMuteAll = useCallback(async () => {
        if (!meetingId) return;
        await muteAllParticipants({ path: { id: meetingId } });
    }, [meetingId]);

    const handleMuteMic = useCallback(
        async (identity: string) => {
            if (!meetingId) return;
            await muteParticipantTrack({
                path: { id: meetingId, identity },
                query: { source: 'microphone' },
            });
        },
        [meetingId],
    );

    const handleMuteCamera = useCallback(
        async (identity: string) => {
            if (!meetingId) return;
            await muteParticipantTrack({
                path: { id: meetingId, identity },
                query: { source: 'camera' },
            });
        },
        [meetingId],
    );

    const handleSend = useCallback(
        (content: string) => {
            const senderName =
                localParticipant.name ?? localParticipant.identity;
            void chat.send(content, senderName);
        },
        [chat, localParticipant],
    );

    function handleOpenChat() {
        setSidebarOpen(true);
        setActiveTab('chat');
    }

    function handleOpenPeople() {
        setSidebarOpen(true);
        setActiveTab('people');
    }

    function handleToolbarStartRecording() {
        setRecordingConfirmOpen(true);
    }

    function handleToolbarStopRecording() {
        void stopRecording();
    }

    const canOpenSettings = Boolean(meetingId);

    return (
        <main className='relative flex h-screen flex-col overflow-hidden bg-meeting-bg'>
            {isReconnecting && (
                <div
                    aria-live='polite'
                    className='shrink-0 bg-yellow-500/10 px-6 py-2 text-center text-sm font-medium text-yellow-600'
                    role='alert'
                >
                    {t('statusReconnecting')}
                </div>
            )}

            <RecordingBanner
                onDismiss={() => setBannerType(null)}
                type={bannerType}
            />

            <div className='flex flex-1 overflow-hidden'>
                {isHostSharing && activeSharer ? (
                    <ScreenShareFullscreen
                        sharerIdentity={activeSharer.identity}
                    />
                ) : (
                    <ParticipantGrid />
                )}
                <MeetingSidebar
                    error={chat.error}
                    activeTab={activeTab}
                    isHost={isHost}
                    isOpen={sidebarOpen}
                    loading={chat.loading}
                    meetingId={meetingId}
                    messages={chat.messages}
                    hasWaitingRoom={hasWaitingRoom}
                    onApprove={waitingRoom.approve}
                    onApproveAll={waitingRoom.approveAll}
                    onClose={() => setSidebarOpen(false)}
                    onDeny={waitingRoom.deny}
                    onLoadHistory={chat.loadHistory}
                    onMuteAll={handleMuteAll}
                    onMuteCamera={handleMuteCamera}
                    onMuteMic={handleMuteMic}
                    onRetry={chat.loadHistory}
                    onSendMessage={handleSend}
                    onTabChange={setActiveTab}
                    participants={sidebarParticipants}
                    sendError={chat.sendError}
                    waitingCount={waitingRoom.pendingCount}
                    waitingRequests={waitingRoom.requests
                        .filter((r) => r.id)
                        .map((r) => ({
                            id: r.id as string,
                            displayName: r.displayName ?? r.id ?? '',
                        }))}
                    userId={userId ?? ''}
                />
            </div>

            <TimerOverlay
                connectedLabel={t('statusConnected')}
                connectionStatus={connectionStatus}
                disconnectedLabel={t('statusDisconnected')}
                displayName={localParticipant.name ?? localParticipant.identity}
                formattedDuration={formattedDuration}
                reconnectingLabel={t('statusReconnecting')}
                shortCode={shortCode ?? undefined}
            />

            <RecordingOverlay isVisible={recordingState === 'recording'} />

            {recordingError !== null && recordingState === 'recording' && (
                <div
                    aria-live='assertive'
                    className='shrink-0 border border-error/40 bg-error-subtle px-6 py-2'
                    role='alert'
                >
                    <div className='flex items-center justify-between gap-4'>
                        <p className='text-sm font-medium text-error-dark'>
                            {recordingError}
                        </p>
                        <div className='flex shrink-0 items-center gap-2'>
                            <Button
                                className='text-error-dark'
                                onClick={() => void stopRecording()}
                                size='sm'
                                type='button'
                                variant='link'
                            >
                                {t('recordingRetry')}
                            </Button>
                            <Button
                                aria-label={t('dismissRecordingBanner')}
                                className='text-error-dark'
                                onClick={clearRecordingError}
                                size='sm'
                                type='button'
                                variant='link'
                            >
                                {t('recordingConfirmCancel')}
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            {screenShareError !== null && (
                <div
                    aria-live='assertive'
                    className='shrink-0 border border-error/40 bg-error-subtle px-6 py-2 text-sm font-medium text-error-dark'
                    role='alert'
                >
                    {t('screenShareError')}
                </div>
            )}

            <MeetingToolbar
                activeSharerName={
                    activeSharer
                        ? (activeSharer.name ?? activeSharer.identity)
                        : null
                }
                canOpenSettings={canOpenSettings}
                canShareScreen={canShareScreen}
                hasWaitingRoom={hasWaitingRoom}
                isHost={isHost}
                isScreenSharing={isLocalSharing}
                onOpenChat={handleOpenChat}
                onOpenPeople={handleOpenPeople}
                onOpenSettings={
                    canOpenSettings ? () => setSettingsOpen(true) : undefined
                }
                onRequestLeave={() => setLeaveDialogOpen(true)}
                onStartRecording={handleToolbarStartRecording}
                onStopRecording={handleToolbarStopRecording}
                onToggleMic={() => void handleToggleMic()}
                onToggleScreenShare={() => void toggleScreenShare()}
                onToggleVideo={() => void handleToggleVideo()}
                participantCount={sidebarParticipants.length}
                pendingWaitingCount={waitingRoom.pendingCount}
                recordingState={recordingState}
                unreadCount={chat.unreadCount}
            />

            <LeaveDialog
                isHost={isHost}
                meetingId={meetingId}
                onOpenChange={setLeaveDialogOpen}
                open={leaveDialogOpen}
            />

            {isHost && (
                <RecordingConfirmDialog
                    error={recordingError}
                    onConfirm={startRecording}
                    onOpenChange={setRecordingConfirmOpen}
                    open={recordingConfirmOpen}
                    recordingState={recordingState}
                />
            )}

            <MeetingSettingsDialog
                meetingId={meetingId}
                onClose={() => setSettingsOpen(false)}
                open={settingsOpen}
            />
        </main>
    );
}

type MeetingBootstrapProps = {
    credentials: NonNullable<SessionCredentials>;
};

function MeetingBootstrap({ credentials }: MeetingBootstrapProps) {
    const [userId, setUserId] = useState<string | null>(null);
    const [hostId, setHostId] = useState<string | null>(null);
    const [hasWaitingRoom, setHasWaitingRoom] = useState(false);
    const [shortCode, setShortCode] = useState<string | null>(null);

    useEffect(() => {
        if (!credentials.meetingId) return;

        Promise.all([
            import('@/generated/sdk.gen.ts').then((sdk) =>
                sdk.getMe({}).then(({ data }) => data?.id ?? null),
            ),
            getMeeting({ path: { id: credentials.meetingId } }).then(
                ({ data }) => ({
                    hostId: data?.hostId ?? null,
                    hasWaitingRoom:
                        data?.settings?.admissionPolicy
                        === ADMISSION_POLICY_WAITING_ROOM,
                    shortCode: data?.shortCode ?? null,
                }),
            ),
        ])
            .then(([resolvedUserId, meetingInfo]) => {
                setUserId(resolvedUserId);
                setHostId(meetingInfo.hostId);
                setHasWaitingRoom(meetingInfo.hasWaitingRoom);
                setShortCode(meetingInfo.shortCode);
            })
            .catch(() => {
                setUserId(null);
                setHostId(null);
                setHasWaitingRoom(false);
                setShortCode(null);
            });
    }, [credentials.meetingId]);

    return (
        <LiveKitRoom
            audio={credentials.audioEnabled}
            data-lk-theme='default'
            serverUrl={liveKitUrl}
            token={credentials.token}
            video={credentials.videoEnabled}
        >
            <MeetingRoomContent
                hasWaitingRoom={hasWaitingRoom}
                hostId={hostId}
                meetingId={credentials.meetingId}
                shortCode={shortCode}
                userId={userId}
            />
        </LiveKitRoom>
    );
}

export function MeetingContainer() {
    const locale = useLocale();
    const t = useTranslations('meetingRoom');

    const [credentials, setCredentials] = useState<
        SessionCredentials | undefined
    >(undefined);
    const consumedRef = useRef(false);

    useEffect(() => {
        if (consumedRef.current) return;
        consumedRef.current = true;
        setCredentials(consumeSessionCredentials());
    }, []);

    if (credentials === undefined) {
        return (
            <main className='flex h-screen flex-col items-center justify-center gap-4 bg-meeting-bg text-white' />
        );
    }

    if (!credentials) {
        return (
            <main className='flex h-screen flex-col items-center justify-center gap-4 bg-meeting-bg text-white'>
                <p className='text-lg'>{t('noActiveSession')}</p>
                <a
                    className='underline opacity-70 hover:opacity-100'
                    href={`/${locale}/workspace`}
                >
                    {t('backToWorkspace')}
                </a>
            </main>
        );
    }

    return <MeetingBootstrap credentials={credentials} />;
}
