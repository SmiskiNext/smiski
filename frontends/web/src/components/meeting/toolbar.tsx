'use client';

import { useLocalParticipant } from '@livekit/components-react';
import {
    Circle,
    Loader2,
    Mic,
    MicOff,
    MonitorOff,
    MonitorUp,
    MoreHorizontal,
    Phone,
    Settings,
    Users,
    Video,
    VideoOff,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu.tsx';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import type { RecordingState } from '@/hooks/use-recording-state.ts';
import { cn } from '@/lib/utils.ts';

type MeetingToolbarProps = {
    isHost: boolean;
    recordingState: RecordingState;
    hasWaitingRoom: boolean;
    pendingWaitingCount: number;
    participantCount: number;
    unreadCount: number;
    canOpenSettings: boolean;
    canShareScreen: boolean;
    isScreenSharing: boolean;
    activeSharerName: string | null;
    onToggleScreenShare: () => void;
    onToggleMic: () => void;
    onToggleVideo: () => void;
    onOpenSettings?: () => void;
    onOpenChat: () => void;
    onOpenPeople: () => void;
    onStartRecording: () => void;
    onStopRecording: () => void;
    onRequestLeave: () => void;
};

type ToolbarIconButtonProps = {
    label: string;
    onClick?: () => void;
    active?: boolean;
    disabled?: boolean;
    children: React.ReactNode;
    className?: string;
    tooltip?: string;
};

const TOOLBAR_BUTTON_BASE =
    'h-11 w-11 rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-60';

const TOOLBAR_BUTTON_IDLE =
    'bg-surface-input text-text-secondary hover:bg-surface-input/80 hover:text-primary';

const TOOLBAR_BUTTON_ACTIVE =
    'bg-error-subtle text-error hover:bg-error-subtle/80';

function ToolbarIconButton({
    label,
    onClick,
    active = false,
    disabled = false,
    children,
    className,
    tooltip,
}: ToolbarIconButtonProps) {
    return (
        <Tooltip>
            <TooltipTrigger asChild>
                <Button
                    aria-label={label}
                    aria-pressed={active}
                    className={
                        className
                        ?? cn(
                            TOOLBAR_BUTTON_BASE,
                            active
                                ? TOOLBAR_BUTTON_ACTIVE
                                : TOOLBAR_BUTTON_IDLE,
                        )
                    }
                    disabled={disabled}
                    onClick={onClick}
                    size='icon'
                    type='button'
                    variant='ghost'
                >
                    {children}
                </Button>
            </TooltipTrigger>
            <TooltipContent side='top'>
                <p>{tooltip ?? label}</p>
            </TooltipContent>
        </Tooltip>
    );
}

function RecordingButton({
    recordingState,
    onStart,
    onStop,
    labelIdle,
    labelStarting,
    labelRecording,
    labelStopping,
}: {
    recordingState: RecordingState;
    onStart: () => void;
    onStop: () => void;
    labelIdle: string;
    labelStarting: string;
    labelRecording: string;
    labelStopping: string;
}) {
    const isDisabled =
        recordingState === 'starting' || recordingState === 'stopping';
    const isRecording = recordingState === 'recording';
    const isStopping = recordingState === 'stopping';

    const label = {
        idle: labelIdle,
        starting: labelStarting,
        recording: labelRecording,
        stopping: labelStopping,
    }[recordingState];

    const buttonClassName =
        isRecording || isStopping
            ? cn(
                  TOOLBAR_BUTTON_BASE,
                  isStopping
                      ? 'bg-error/70 text-white'
                      : 'bg-error text-white shadow-[0_0_12px_rgba(220,38,38,0.5)]',
              )
            : cn(TOOLBAR_BUTTON_BASE, TOOLBAR_BUTTON_IDLE);

    function handleClick() {
        if (isRecording) {
            onStop();
        } else if (recordingState === 'idle') {
            onStart();
        }
    }

    return (
        <ToolbarIconButton
            className={buttonClassName}
            disabled={isDisabled}
            label={label}
            onClick={handleClick}
        >
            {isDisabled ? (
                <Loader2 className='h-5 w-5 animate-spin' />
            ) : isRecording ? (
                <Circle className='h-5 w-5 fill-white text-white' />
            ) : (
                <Circle className='h-5 w-5' />
            )}
        </ToolbarIconButton>
    );
}

/**
 * Floating pill toolbar centered at the bottom of the meeting room.
 * Exposes mic, video, screen-share, recording, waiting-room, more,
 * and leave controls. Host-only buttons (waiting room, recording) render
 * conditionally. Shows an unread badge on the chat menu item when
 * unreadCount > 0.
 */
export function MeetingToolbar({
    isHost,
    recordingState,
    hasWaitingRoom,
    pendingWaitingCount,
    participantCount,
    unreadCount,
    canOpenSettings,
    canShareScreen,
    isScreenSharing,
    activeSharerName,
    onToggleScreenShare,
    onToggleMic,
    onToggleVideo,
    onOpenSettings,
    onOpenChat,
    onOpenPeople,
    onStartRecording,
    onStopRecording,
    onRequestLeave,
}: MeetingToolbarProps) {
    const t = useTranslations('meetingRoom');
    const { isMicrophoneEnabled, isCameraEnabled } = useLocalParticipant();

    const isScreenShareDisabled = !canShareScreen && !isScreenSharing;
    const screenShareLabel = isScreenSharing
        ? t('controlStopScreenShare')
        : t('controlScreenShare');
    const screenShareTooltip = isScreenSharing
        ? t('controlStopScreenShare')
        : activeSharerName
          ? t('screenShareInUse', { name: activeSharerName })
          : isScreenShareDisabled
            ? t('screenShareUnavailable')
            : t('controlScreenShare');

    return (
        <TooltipProvider>
            <footer className='pointer-events-none absolute inset-x-0 bottom-6 z-20 flex justify-center'>
                <div className='pointer-events-auto flex items-center gap-2 rounded-full border border-border/60 bg-surface px-4 py-3 shadow-[0_8px_32px_-8px_rgba(0,0,0,0.25)] backdrop-blur-sm'>
                    <ToolbarIconButton
                        active={!isMicrophoneEnabled}
                        label={
                            isMicrophoneEnabled
                                ? t('controlMic')
                                : t('controlMicOff')
                        }
                        onClick={onToggleMic}
                    >
                        {isMicrophoneEnabled ? (
                            <Mic className='h-5 w-5' />
                        ) : (
                            <MicOff className='h-5 w-5' />
                        )}
                    </ToolbarIconButton>

                    <ToolbarIconButton
                        active={!isCameraEnabled}
                        label={
                            isCameraEnabled
                                ? t('controlVideo')
                                : t('controlVideoOff')
                        }
                        onClick={onToggleVideo}
                    >
                        {isCameraEnabled ? (
                            <Video className='h-5 w-5' />
                        ) : (
                            <VideoOff className='h-5 w-5' />
                        )}
                    </ToolbarIconButton>

                    <ToolbarIconButton
                        active={isScreenSharing}
                        disabled={isScreenShareDisabled}
                        label={screenShareLabel}
                        onClick={onToggleScreenShare}
                        tooltip={screenShareTooltip}
                    >
                        {isScreenSharing ? (
                            <MonitorOff className='h-5 w-5' />
                        ) : (
                            <MonitorUp className='h-5 w-5' />
                        )}
                    </ToolbarIconButton>

                    {isHost && (
                        <RecordingButton
                            labelIdle={t('controlStartRecording')}
                            labelRecording={t('controlStopRecording')}
                            labelStarting={t('recordingStarting')}
                            labelStopping={t('recordingStopping')}
                            onStart={onStartRecording}
                            onStop={onStopRecording}
                            recordingState={recordingState}
                        />
                    )}

                    <DropdownMenu>
                        <Tooltip>
                            <TooltipTrigger asChild>
                                <DropdownMenuTrigger asChild>
                                    <Button
                                        aria-label={t('controlMore')}
                                        className={cn(
                                            TOOLBAR_BUTTON_BASE,
                                            TOOLBAR_BUTTON_IDLE,
                                        )}
                                        size='icon'
                                        type='button'
                                        variant='ghost'
                                    >
                                        <MoreHorizontal className='h-5 w-5' />
                                    </Button>
                                </DropdownMenuTrigger>
                            </TooltipTrigger>
                            <TooltipContent side='top'>
                                <p>{t('controlMore')}</p>
                            </TooltipContent>
                        </Tooltip>
                        <DropdownMenuContent align='center' side='top'>
                            <DropdownMenuItem onClick={onOpenPeople}>
                                <Users className='mr-2 h-4 w-4' />
                                <span className='flex items-center gap-2'>
                                    {t('controlPeople')}
                                    <span className='flex h-5 min-w-5 items-center justify-center rounded-full bg-surface-input px-1 text-[0.7rem] font-semibold'>
                                        {participantCount}
                                    </span>
                                    {isHost
                                        && hasWaitingRoom
                                        && pendingWaitingCount > 0 && (
                                            <span className='flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-[0.7rem] font-semibold text-white'>
                                                {pendingWaitingCount}
                                            </span>
                                        )}
                                </span>
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={onOpenChat}>
                                <span className='flex items-center gap-2'>
                                    {t('controlChat')}
                                    {unreadCount > 0 && (
                                        <span className='flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-[0.7rem] font-semibold text-white'>
                                            {unreadCount}
                                        </span>
                                    )}
                                </span>
                            </DropdownMenuItem>
                            {canOpenSettings && onOpenSettings && (
                                <DropdownMenuItem onClick={onOpenSettings}>
                                    <Settings className='mr-2 h-4 w-4' />
                                    {t('controlSettings')}
                                </DropdownMenuItem>
                            )}
                        </DropdownMenuContent>
                    </DropdownMenu>

                    <div className='mx-1 h-8 w-px bg-border' />

                    <Tooltip>
                        <TooltipTrigger asChild>
                            <Button
                                className='h-11 rounded-full bg-error px-5 text-white shadow-[0_8px_24px_-8px_rgba(220,38,38,0.7)] hover:bg-error/90'
                                onClick={onRequestLeave}
                                type='button'
                                variant='destructive'
                            >
                                <Phone className='h-4 w-4 rotate-[135deg]' />
                                <span className='ml-1.5 text-sm font-medium'>
                                    {t('leave')}
                                </span>
                            </Button>
                        </TooltipTrigger>
                        <TooltipContent side='top'>
                            <p>{t('leaveDialogTitle')}</p>
                        </TooltipContent>
                    </Tooltip>
                </div>
            </footer>
        </TooltipProvider>
    );
}
