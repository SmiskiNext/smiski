'use client';

import {
    Camera,
    CameraOff,
    Check,
    Loader2,
    MessageSquare,
    Mic,
    MicOff,
    Search,
    Users,
    X,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';
import {
    Avatar,
    AvatarFallback,
    AvatarImage,
} from '@/components/ui/avatar.tsx';
import { cn } from '@/lib/utils.ts';
import type { ChatMessage } from '@/types/chat.ts';
import { MeetingChat } from './chat.tsx';
import type { ParticipantViewModel } from './types.ts';

type SidebarTab = 'chat' | 'people';

type MuteAllState = 'idle' | 'loading' | 'success';

type WaitingRequest = {
    id: string;
    displayName: string;
};

type MeetingSidebarProps = {
    participants: ParticipantViewModel[];
    waitingRequests: WaitingRequest[];
    waitingCount: number;
    messages: ChatMessage[];
    loading: boolean;
    error: boolean;
    sendError: boolean;
    isOpen: boolean;
    isHost: boolean;
    activeTab: SidebarTab;
    meetingId: string | null;
    userId: string;
    onClose: () => void;
    onTabChange: (tab: SidebarTab) => void;
    onApprove: (id: string) => void;
    onDeny: (id: string) => void;
    onApproveAll: () => void;
    onSendMessage: (content: string) => void;
    onRetry: () => void;
    onLoadHistory: () => void;
    onMuteAll: () => Promise<void>;
    onMuteMic: (identity: string) => Promise<void>;
    onMuteCamera: (identity: string) => Promise<void>;
    hasWaitingRoom: boolean;
};

type LoadingTrack = 'mic' | 'camera';

type ParticipantRowProps = {
    participant: ParticipantViewModel;
    isHost: boolean;
    onMuteMic: (identity: string) => Promise<void>;
    onMuteCamera: (identity: string) => Promise<void>;
    onError: (message: string) => void;
};

function participantInitials(displayName: string): string {
    return displayName
        .split(' ')
        .map((part) => part[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();
}

function isModerableRow(
    participant: ParticipantViewModel,
    isHost: boolean,
): boolean {
    return isHost && !participant.isLocal && participant.role !== 'HOST';
}

function ParticipantRow({
    participant,
    isHost,
    onMuteMic,
    onMuteCamera,
    onError,
}: ParticipantRowProps) {
    const t = useTranslations('meetingRoom');
    const [loadingTrack, setLoadingTrack] = useState<LoadingTrack | null>(null);
    const moderable = isModerableRow(participant, isHost);

    async function handleMuteMic() {
        setLoadingTrack('mic');
        try {
            await onMuteMic(participant.identity);
        } catch {
            onError(t('moderationMuteError'));
        } finally {
            setLoadingTrack(null);
        }
    }

    async function handleMuteCamera() {
        setLoadingTrack('camera');
        try {
            await onMuteCamera(participant.identity);
        } catch {
            onError(t('moderationMuteError'));
        } finally {
            setLoadingTrack(null);
        }
    }

    return (
        <div className='flex items-center gap-4 rounded-[1.2rem] bg-surface-person-item px-5 py-4'>
            <Avatar className='h-11 w-11 shrink-0 shadow-sm'>
                {participant.avatarUrl && (
                    <AvatarImage
                        alt={participant.displayName}
                        src={participant.avatarUrl}
                    />
                )}
                <AvatarFallback className='bg-gradient-to-br from-[var(--avatar-gradient-navy-start)] to-[var(--avatar-gradient-navy-end)] text-sm font-semibold text-white'>
                    {participantInitials(participant.displayName)}
                </AvatarFallback>
            </Avatar>
            <div className='min-w-0 flex-1'>
                <p className='truncate text-[1.05rem] font-semibold text-text-dark'>
                    {participant.displayName}
                </p>
                {participant.isLocal && (
                    <p className='text-[0.85rem] text-primary'>{t('you')}</p>
                )}
                {participant.role === 'HOST' && !participant.isLocal && (
                    <p className='text-[0.85rem] text-text-muted'>
                        {t('host')}
                    </p>
                )}
                {participant.role === 'GUEST' && !participant.isLocal && (
                    <p className='text-[0.85rem] text-text-muted'>
                        {t('guest')}
                    </p>
                )}
            </div>

            {moderable ? (
                <div className='flex items-center gap-1'>
                    <button
                        aria-label={t('moderationMuteMic', {
                            name: participant.displayName,
                        })}
                        className='flex h-8 w-8 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-input hover:text-text-dark disabled:cursor-not-allowed disabled:opacity-50'
                        disabled={loadingTrack !== null}
                        onClick={() => void handleMuteMic()}
                        title={t('moderationMuteMicTooltip')}
                        type='button'
                    >
                        {loadingTrack === 'mic' ? (
                            <Loader2 className='h-4 w-4 animate-spin' />
                        ) : participant.isMicEnabled ? (
                            <Mic className='h-4 w-4' />
                        ) : (
                            <MicOff className='h-4 w-4' />
                        )}
                    </button>
                    <button
                        aria-label={t('moderationMuteCamera', {
                            name: participant.displayName,
                        })}
                        className='flex h-8 w-8 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-input hover:text-text-dark disabled:cursor-not-allowed disabled:opacity-50'
                        disabled={loadingTrack !== null}
                        onClick={() => void handleMuteCamera()}
                        title={t('moderationMuteCameraTooltip')}
                        type='button'
                    >
                        {loadingTrack === 'camera' ? (
                            <Loader2 className='h-4 w-4 animate-spin' />
                        ) : participant.isCameraEnabled ? (
                            <Camera className='h-4 w-4' />
                        ) : (
                            <CameraOff className='h-4 w-4' />
                        )}
                    </button>
                </div>
            ) : (
                <span
                    className={
                        participant.isMicEnabled
                            ? 'text-primary'
                            : 'text-border-muted-disabled'
                    }
                >
                    {participant.isMicEnabled ? (
                        <Mic className='h-4 w-4' />
                    ) : (
                        <MicOff className='h-4 w-4' />
                    )}
                </span>
            )}
        </div>
    );
}

type MuteAllBannerProps = {
    onMuteAll: () => Promise<void>;
    onError: (message: string) => void;
};

function MuteAllBanner({ onMuteAll, onError }: MuteAllBannerProps) {
    const t = useTranslations('meetingRoom');
    const [state, setState] = useState<MuteAllState>('idle');

    async function handleMuteAll() {
        setState('loading');
        try {
            await onMuteAll();
            setState('success');
            setTimeout(() => setState('idle'), 2000);
        } catch {
            setState('idle');
            onError(t('moderationMuteAllError'));
        }
    }

    return (
        <div className='sticky top-0 z-10 shrink-0 border-b border-border bg-surface px-5 py-3'>
            <button
                className='flex w-full items-center justify-center gap-2 rounded-lg bg-surface-input px-4 py-2.5 text-sm font-medium text-text-dark transition-colors hover:bg-surface-person-item disabled:cursor-not-allowed disabled:opacity-60'
                disabled={state === 'loading'}
                onClick={() => void handleMuteAll()}
                type='button'
            >
                {state === 'loading' ? (
                    <>
                        <Loader2 className='h-4 w-4 animate-spin' />
                        {t('moderationMuteAllLoading')}
                    </>
                ) : state === 'success' ? (
                    <>
                        <MicOff className='h-4 w-4' />
                        {t('moderationMuteAllSuccess')}
                    </>
                ) : (
                    <>
                        <MicOff className='h-4 w-4' />
                        {t('moderationMuteAll')}
                    </>
                )}
            </button>
        </div>
    );
}

/**
 * Meeting sidebar with chat and people tabs. On desktop it renders inline;
 * on smaller screens it renders as a dismissible overlay drawer.
 *
 * When `isHost` is true and `meetingId` is set, the People tab exposes
 * per-participant mute controls and a sticky mute-all banner for moderable
 * rows. Non-host users see a read-only participant list.
 *
 * History load is triggered once when the chat tab becomes active for the
 * first time.
 */
export function MeetingSidebar({
    participants,
    waitingRequests,
    waitingCount,
    messages,
    loading,
    error,
    sendError,
    isOpen,
    isHost,
    activeTab,
    meetingId,
    userId,
    onClose,
    onTabChange,
    onApprove,
    onDeny,
    onApproveAll,
    onSendMessage,
    onRetry,
    onLoadHistory,
    onMuteAll,
    onMuteMic,
    onMuteCamera,
    hasWaitingRoom,
}: MeetingSidebarProps) {
    const t = useTranslations('meetingRoom');
    const [moderationError, setModerationError] = useState<string | null>(null);
    const [participantSearch, setParticipantSearch] = useState('');
    const chatHistoryTriggeredRef = useRef(false);

    const hostCanModerate = isHost && Boolean(meetingId);
    const normalizedParticipantSearch = participantSearch.trim().toLowerCase();
    const filteredParticipants = normalizedParticipantSearch
        ? participants.filter((participant) =>
              participant.displayName
                  .toLowerCase()
                  .includes(normalizedParticipantSearch),
          )
        : participants;

    function clearError() {
        setModerationError(null);
    }

    function handleChatTabActivate() {
        onTabChange('chat');
        if (!chatHistoryTriggeredRef.current) {
            chatHistoryTriggeredRef.current = true;
            onLoadHistory();
        }
    }

    useEffect(() => {
        if (
            isOpen
            && activeTab === 'chat'
            && !chatHistoryTriggeredRef.current
        ) {
            chatHistoryTriggeredRef.current = true;
            onLoadHistory();
        }
    }, [isOpen, activeTab, onLoadHistory]);

    return (
        <>
            {isOpen && (
                <div
                    aria-hidden='true'
                    className='fixed inset-0 z-30 bg-black/50 md:hidden'
                    onClick={onClose}
                />
            )}

            <aside
                className={cn(
                    'flex flex-col border-l border-border bg-surface transition-all duration-300',
                    'fixed inset-y-0 right-0 z-40 w-[300px] md:relative md:z-auto md:inset-auto xl:w-[340px]',
                    isOpen
                        ? 'translate-x-0'
                        : 'translate-x-full md:hidden md:translate-x-0',
                )}
            >
                <div className='shrink-0 border-b border-border px-6 py-5'>
                    <div className='flex items-center justify-between'>
                        <h2 className='text-[1.5rem] font-semibold tracking-tight text-text-dark'>
                            {t('meetingDetails')}
                        </h2>
                        <button
                            aria-label={t('closeSidebar')}
                            className='flex h-8 w-8 items-center justify-center rounded-full text-text-muted transition-colors hover:bg-surface-input hover:text-text-dark md:hidden'
                            onClick={onClose}
                            type='button'
                        >
                            <X className='h-4 w-4' />
                        </button>
                    </div>
                    <p className='mt-1 text-[0.95rem] text-text-muted'>
                        {t('activeSession', { count: participants.length })}
                    </p>
                </div>

                <div className='flex shrink-0 border-b border-border'>
                    <button
                        className={`flex flex-1 items-center justify-center gap-2 border-b-2 px-4 py-4 text-[1.02rem] font-medium transition-colors ${
                            activeTab === 'chat'
                                ? 'border-primary text-primary'
                                : 'border-transparent text-text-subtle hover:text-text-secondary'
                        }`}
                        onClick={handleChatTabActivate}
                        type='button'
                    >
                        <MessageSquare className='h-6 w-6' />
                        {t('tabChat')}
                    </button>
                    <button
                        className={`flex flex-1 items-center justify-center gap-2 border-b-2 px-4 py-4 text-[1.02rem] font-medium transition-colors ${
                            activeTab === 'people'
                                ? 'border-primary text-primary'
                                : 'border-transparent text-text-subtle hover:text-text-secondary'
                        }`}
                        onClick={() => {
                            onTabChange('people');
                            clearError();
                        }}
                        type='button'
                    >
                        <Users className='h-6 w-6' />
                        {t('tabPeople')}
                    </button>
                </div>

                {activeTab === 'chat' ? (
                    <MeetingChat
                        error={error}
                        loading={loading}
                        messages={messages}
                        onRetry={onRetry}
                        onSend={onSendMessage}
                        sendError={sendError}
                        userId={userId}
                    />
                ) : (
                    <div className='flex flex-1 flex-col overflow-hidden'>
                        {hostCanModerate && (
                            <MuteAllBanner
                                onError={setModerationError}
                                onMuteAll={onMuteAll}
                            />
                        )}

                        {moderationError && (
                            <div
                                aria-live='polite'
                                className='shrink-0 px-5 py-2 text-sm text-error'
                                role='alert'
                            >
                                {moderationError}
                            </div>
                        )}

                        <div className='shrink-0 px-5 pt-5'>
                            <label className='relative block'>
                                <span className='sr-only'>
                                    {t('participantSearch')}
                                </span>
                                <Search className='absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted' />
                                <input
                                    className='w-full rounded-full border border-border bg-surface-input py-2.5 pr-4 pl-11 text-sm text-text-dark outline-none transition-colors placeholder:text-text-muted focus:border-primary'
                                    onChange={(event) =>
                                        setParticipantSearch(event.target.value)
                                    }
                                    placeholder={t('participantSearch')}
                                    type='search'
                                    value={participantSearch}
                                />
                            </label>
                        </div>

                        <div className='flex-1 space-y-3 overflow-y-auto p-5'>
                            {filteredParticipants.map((p) => (
                                <ParticipantRow
                                    isHost={isHost}
                                    key={p.identity}
                                    onError={setModerationError}
                                    onMuteCamera={onMuteCamera}
                                    onMuteMic={onMuteMic}
                                    participant={p}
                                />
                            ))}

                            {isHost && hasWaitingRoom && waitingCount > 0 && (
                                <div className='border-t border-border pt-4'>
                                    <div className='flex items-center justify-between px-1 pb-3'>
                                        <h3 className='text-sm font-semibold text-text-dark'>
                                            {t('waitingRoomTitle')} (
                                            {waitingCount})
                                        </h3>
                                        <button
                                            className='text-xs font-medium text-primary hover:text-primary-dark'
                                            onClick={onApproveAll}
                                            type='button'
                                        >
                                            {t('waitingRoomApproveAll')}
                                        </button>
                                    </div>
                                    <div className='space-y-2'>
                                        {waitingRequests.map((req) => (
                                            <div
                                                className='flex items-center gap-3 rounded-[1.2rem] bg-surface-person-item px-5 py-3'
                                                key={req.id}
                                            >
                                                <Avatar className='h-9 w-9 shrink-0'>
                                                    <AvatarFallback className='bg-gradient-to-br from-[var(--avatar-gradient-navy-start)] to-[var(--avatar-gradient-navy-end)] text-xs font-semibold text-white'>
                                                        {participantInitials(
                                                            req.displayName,
                                                        )}
                                                    </AvatarFallback>
                                                </Avatar>
                                                <span className='min-w-0 flex-1 truncate text-sm font-medium text-text-dark'>
                                                    {req.displayName}
                                                </span>
                                                <div className='flex items-center gap-1'>
                                                    <button
                                                        className='flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-primary hover:bg-primary/20'
                                                        onClick={() =>
                                                            onApprove(req.id)
                                                        }
                                                        type='button'
                                                    >
                                                        <Check className='h-3.5 w-3.5' />
                                                    </button>
                                                    <button
                                                        className='flex h-7 w-7 items-center justify-center rounded-full bg-error/10 text-error hover:bg-error/20'
                                                        onClick={() =>
                                                            onDeny(req.id)
                                                        }
                                                        type='button'
                                                    >
                                                        <X className='h-3.5 w-3.5' />
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </aside>
        </>
    );
}
