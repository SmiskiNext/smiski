import { useEffect, useState } from 'react';
import { cn, Icon } from '../../../components/ui';
import {
    type LayoutMode,
    type Meeting,
    type Participant,
    resolveMeetingLayout,
} from '../../../domain';
import type {
    LiveKitConnectionState,
    LiveMeetingParticipant,
    ScreenShareFeed,
} from '../../../hooks/useLiveKitRoom';
import { MeetingLayoutMenu } from './MeetingLayoutMenu';
import { ParticipantFilmstrip } from './ParticipantFilmstrip';
import { ParticipantVideoGrid, tileMediaProps } from './ParticipantVideoGrid';
import { ParticipantVideoTile } from './ParticipantVideoTile';
import { ScreenShareStage } from './ScreenShareStage';

export interface MeetingRoomShellProps {
    meeting: Meeting | null;
    participants: (Participant | LiveMeetingParticipant)[];
    selfAccountId: string;
    isPeoplePanelOpen: boolean;
    onTogglePeoplePanel: () => void;
    onLeave?: () => void;
    /**
     * Real media state/handlers, supplied by the caller from
     * `useLiveKitRoom`'s live room connection.
     */
    isMicOn: boolean;
    isCameraOn: boolean;
    isScreenSharing: boolean;
    /**
     * Set while anyone in the room is presenting, `null` when nobody is. One
     * of the inputs `resolveMeetingLayout` weighs when choosing between the
     * equal grid and a stage + filmstrip.
     */
    screenShare: ScreenShareFeed | null;
    onToggleMic: () => void;
    onToggleCamera: () => void;
    onToggleScreenShare: () => void;
    /** Chosen arrangement, persisted per user by the caller. */
    layoutMode: LayoutMode;
    onLayoutModeChange: (mode: LayoutMode) => void;
    /**
     * Participant held on the stage regardless of who is speaking or
     * presenting, or `null` when nothing is pinned. Session-only — the caller
     * neither persists it nor lets it outlive the pinned participant.
     */
    pinnedAccountId: string | null;
    onTogglePin: (accountId: string) => void;
    /** Loudest participant right now, or `null` while the room is silent. */
    activeSpeakerId: string | null;
    /**
     * User-facing note about a media-permission change — a failed
     * screen-share toggle, or the host revoking mic/camera/screen-share
     * access mid-session (see `useLiveKitRoom`'s `mediaNotice`). `null` when
     * there is nothing to report.
     */
    mediaNotice: string | null;
    /** Surfaces `useLiveKitRoom`'s connection state for debugging/trial visibility. */
    connectionState: LiveKitConnectionState;
    /**
     * Opens `MeetingSettingsModal` for this meeting. Shown to everyone in the
     * room, not just the host: the modal carries a personal notification
     * preference alongside the host-only room settings, and gates the host
     * section itself.
     */
    onOpenSettings?: () => void;
    /**
     * Host-only pending-requests toggle. Absent for non-hosts so the right
     * footer zone stays empty.
     */
    pendingJoinRequestCount?: number;
    isPendingPanelOpen?: boolean;
    onTogglePendingPanel?: () => void;
}

function useElapsedTime(startTime?: string): string {
    const [now, setNow] = useState(() => Date.now());
    useEffect(() => {
        const interval = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(interval);
    }, []);
    if (!startTime) return '00:00';
    const elapsed = Math.max(
        0,
        Math.floor((now - new Date(startTime).getTime()) / 1000),
    );
    const hours = Math.floor(elapsed / 3600);
    const minutes = Math.floor((elapsed % 3600) / 60);
    const seconds = elapsed % 60;
    const pad = (value: number) => value.toString().padStart(2, '0');
    return hours
        ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
        : `${pad(minutes)}:${pad(seconds)}`;
}

function ControlButton({
    label,
    icon,
    active = true,
    danger,
    disabled,
    title,
    onClick,
    badge,
}: {
    label: string;
    icon:
        | 'mic'
        | 'micOff'
        | 'camera'
        | 'cameraOff'
        | 'screen'
        | 'people'
        | 'phoneOff'
        | 'settings'
        | 'layout'
        | 'userPlus';
    active?: boolean;
    danger?: boolean;
    disabled?: boolean;
    title?: string;
    onClick?: () => void;
    badge?: number;
}) {
    return (
        <button
            type='button'
            aria-label={label}
            aria-pressed={!danger ? active : undefined}
            disabled={disabled}
            title={title}
            onClick={onClick}
            className={cn(
                'group flex min-w-16 flex-col items-center gap-1.5 text-[10px] font-semibold transition',
                danger ? 'text-red-300' : 'text-slate-300',
                disabled && 'cursor-not-allowed opacity-40',
            )}
        >
            <span
                className={cn(
                    'relative flex size-10 items-center justify-center rounded-full border transition sm:size-11',
                    danger
                        ? 'border-red-500 bg-red-600 text-white hover:bg-red-700'
                        : active
                          ? 'border-white/10 bg-white/10 text-white hover:bg-white/20'
                          : 'border-white/5 bg-white/5 text-slate-400 hover:bg-white/10',
                    disabled && 'hover:bg-white/5',
                )}
            >
                <Icon name={icon} size={18} />
                {badge !== undefined && (
                    <span className='absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full bg-brand-500 text-[9px] font-bold text-white ring-2 ring-slate-950'>
                        {badge}
                    </span>
                )}
            </span>
            <span className='hidden sm:block'>{label}</span>
        </button>
    );
}

/**
 * Presentational meeting room: header, video area and the media control bar.
 * All media state and handlers come from the caller's live LiveKit room.
 *
 * Host-configured media permissions gate the mic/camera/screen-share buttons,
 * but each gate defaults to allowed when `meeting.settings` is absent — a
 * meeting summary without settings must not lock the controls out.
 */
export function MeetingRoomShell({
    meeting,
    participants,
    selfAccountId,
    isPeoplePanelOpen,
    onTogglePeoplePanel,
    onLeave,
    isMicOn,
    isCameraOn,
    isScreenSharing,
    screenShare,
    onToggleMic,
    onToggleCamera,
    onToggleScreenShare,
    mediaNotice,
    connectionState,
    onOpenSettings,
    layoutMode,
    onLayoutModeChange,
    pinnedAccountId,
    onTogglePin,
    activeSpeakerId,
    pendingJoinRequestCount,
    isPendingPanelOpen = false,
    onTogglePendingPanel,
}: MeetingRoomShellProps) {
    const canShareScreen = meeting?.settings?.allowScreenShare ?? true;
    const canUseMic = meeting?.settings?.allowMicrophone ?? true;
    const canUseCamera = meeting?.settings?.allowVideo ?? true;
    const elapsed = useElapsedTime(meeting?.startedAt);
    const layout = resolveMeetingLayout({
        mode: layoutMode,
        participants,
        selfAccountId,
        pinnedAccountId,
        hasScreenShare: screenShare !== null,
        activeSpeakerId,
    });
    // Lifted out of the JSX so the discriminant narrowing below survives into
    // the pin callback: TypeScript drops narrowings of a property access (but
    // not of a `const` binding) inside a closure.
    const stageSubject = layout.arrangement === 'stage' ? layout.subject : null;
    return (
        <section className='overflow-hidden rounded-3xl border border-slate-700 bg-slate-950 shadow-panel'>
            <header className='flex items-center justify-between gap-4 border-b border-white/8 px-4 py-3.5 sm:px-5'>
                <div className='min-w-0'>
                    <h2 className='truncate text-sm font-bold text-white'>
                        {meeting?.title ?? 'Meeting room'}
                    </h2>
                    <p className='mt-0.5 text-xs text-slate-400'>
                        {meeting?.issueKey ?? 'Smiski project room'}
                    </p>
                </div>
                <div className='flex shrink-0 items-center gap-2'>
                    {connectionState && (
                        <span
                            className={cn(
                                'rounded-full px-2.5 py-1 text-[10px] font-bold tracking-wide uppercase',
                                connectionState === 'connecting'
                                    && 'bg-amber-500/20 text-amber-300',
                                connectionState === 'error'
                                    && 'bg-red-500/20 text-red-300',
                                connectionState === 'connected'
                                    && 'bg-emerald-500/20 text-emerald-300',
                                (connectionState === 'idle'
                                    || connectionState === 'disconnected')
                                    && 'bg-white/10 text-slate-300',
                            )}
                        >
                            {connectionState === 'connecting'
                                ? 'Connecting video…'
                                : connectionState === 'connected'
                                  ? 'LiveKit connected'
                                  : connectionState}
                        </span>
                    )}
                    <span className='inline-flex items-center gap-2 rounded-full bg-white/8 px-3 py-1.5 font-mono text-xs text-slate-300'>
                        <span className='size-1.5 animate-pulse rounded-full bg-emerald-400' />
                        {elapsed}
                    </span>
                </div>
            </header>
            {layout.arrangement === 'stage' && stageSubject ? (
                <div className='flex h-80 flex-col gap-3 bg-slate-900 p-3 sm:p-4 lg:h-120 lg:flex-row'>
                    <div className='min-h-0 min-w-0 flex-1'>
                        {stageSubject.kind === 'screenShare' ? (
                            screenShare && (
                                <ScreenShareStage feed={screenShare} />
                            )
                        ) : (
                            <ParticipantVideoTile
                                participant={stageSubject.participant}
                                {...tileMediaProps(
                                    stageSubject.participant,
                                    selfAccountId,
                                    isMicOn,
                                    activeSpeakerId,
                                )}
                                isPinned={
                                    stageSubject.participant.accountId
                                    === pinnedAccountId
                                }
                                onTogglePin={() =>
                                    onTogglePin(
                                        stageSubject.participant.accountId,
                                    )
                                }
                            />
                        )}
                    </div>
                    <ParticipantFilmstrip
                        cells={layout.cells}
                        selfAccountId={selfAccountId}
                        isSelfMicOn={isMicOn}
                        activeSpeakerId={activeSpeakerId}
                        pinnedAccountId={pinnedAccountId}
                        onTogglePin={onTogglePin}
                    />
                </div>
            ) : (
                <ParticipantVideoGrid
                    cells={layout.cells}
                    selfAccountId={selfAccountId}
                    isSelfMicOn={isMicOn}
                    screenShare={screenShare}
                    activeSpeakerId={activeSpeakerId}
                    pinnedAccountId={pinnedAccountId}
                    onTogglePin={onTogglePin}
                />
            )}
            <footer className='border-t border-white/8 bg-slate-950 px-3 py-4'>
                <div className='grid grid-cols-[1fr_auto_1fr] items-center gap-3'>
                    <div />
                    <div className='flex items-center justify-center gap-3 sm:gap-5'>
                        <ControlButton
                            label={
                                !canUseMic && !isMicOn
                                    ? 'Microphone off'
                                    : isMicOn
                                      ? 'Mute'
                                      : 'Unmute'
                            }
                            icon={isMicOn ? 'mic' : 'micOff'}
                            active={isMicOn}
                            disabled={!canUseMic && !isMicOn}
                            title={
                                !canUseMic && !isMicOn
                                    ? 'The host has disabled the microphone for this meeting'
                                    : undefined
                            }
                            onClick={onToggleMic}
                        />
                        <ControlButton
                            label={
                                !canUseCamera && !isCameraOn
                                    ? 'Video off'
                                    : isCameraOn
                                      ? 'Stop video'
                                      : 'Start video'
                            }
                            icon={isCameraOn ? 'camera' : 'cameraOff'}
                            active={isCameraOn}
                            disabled={!canUseCamera && !isCameraOn}
                            title={
                                !canUseCamera && !isCameraOn
                                    ? 'The host has disabled video for this meeting'
                                    : undefined
                            }
                            onClick={onToggleCamera}
                        />
                        <ControlButton
                            label={
                                !canShareScreen && !isScreenSharing
                                    ? 'Screen share off'
                                    : isScreenSharing
                                      ? 'Stop sharing'
                                      : 'Share screen'
                            }
                            icon='screen'
                            active={isScreenSharing}
                            disabled={!canShareScreen && !isScreenSharing}
                            title={
                                !canShareScreen && !isScreenSharing
                                    ? 'The host has disabled screen sharing for this meeting'
                                    : undefined
                            }
                            onClick={onToggleScreenShare}
                        />
                        <MeetingLayoutMenu
                            mode={layoutMode}
                            onModeChange={onLayoutModeChange}
                        />
                        {onOpenSettings && (
                            <ControlButton
                                label='Settings'
                                icon='settings'
                                active={false}
                                onClick={onOpenSettings}
                            />
                        )}
                        <ControlButton
                            label='People'
                            icon='people'
                            active={isPeoplePanelOpen}
                            onClick={onTogglePeoplePanel}
                            badge={participants.length}
                        />
                        <ControlButton
                            label='Leave'
                            icon='phoneOff'
                            danger
                            onClick={onLeave}
                        />
                    </div>
                    <div className='flex items-center justify-end'>
                        {onTogglePendingPanel && (
                            <ControlButton
                                label='Requests'
                                icon='userPlus'
                                active={isPendingPanelOpen}
                                onClick={onTogglePendingPanel}
                                badge={pendingJoinRequestCount}
                            />
                        )}
                    </div>
                </div>
                {mediaNotice && (
                    <p className='mt-2 text-center text-[11px] font-medium text-red-300'>
                        {mediaNotice}
                    </p>
                )}
            </footer>
        </section>
    );
}
