import { useEffect, useState } from 'react';
import { cn, Icon } from '../../../components/ui';
import type { Meeting, Participant } from '../../../domain';
import type {
    LiveKitConnectionState,
    LiveMeetingParticipant,
} from '../../../hooks/useLiveKitRoom';
import { ParticipantVideoGrid } from './ParticipantVideoGrid';

export interface MeetingRoomShellProps {
    meeting: Meeting | null;
    participants: (Participant | LiveMeetingParticipant)[];
    selfAccountId: string;
    isPeoplePanelOpen: boolean;
    onTogglePeoplePanel: () => void;
    onLeave?: () => void;
    /**
     * Real media state/handlers (from `useLiveKitRoom`) when connected to a
     * live LiveKit room. Omitted (undefined) in standalone `pnpm ui:dev` — the
     * component falls back to local `useState` so the controls stay visually
     * testable without a Forge bridge.
     */
    isMicOn?: boolean;
    isCameraOn?: boolean;
    isScreenSharing?: boolean;
    onToggleMic?: () => void;
    onToggleCamera?: () => void;
    onToggleScreenShare?: () => void;
    /** Surfaces `useLiveKitRoom`'s connection state for debugging/trial visibility. */
    connectionState?: LiveKitConnectionState;
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
        | 'phoneOff';
    active?: boolean;
    danger?: boolean;
    onClick?: () => void;
    badge?: number;
}) {
    return (
        <button
            type='button'
            aria-label={label}
            aria-pressed={!danger ? active : undefined}
            onClick={onClick}
            className={cn(
                'group flex min-w-16 flex-col items-center gap-1.5 text-[10px] font-semibold transition',
                danger ? 'text-red-300' : 'text-slate-300',
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

export function MeetingRoomShell({
    meeting,
    participants,
    selfAccountId,
    isPeoplePanelOpen,
    onTogglePeoplePanel,
    onLeave,
    isMicOn: isMicOnProp,
    isCameraOn: isCameraOnProp,
    isScreenSharing: isScreenSharingProp,
    onToggleMic,
    onToggleCamera,
    onToggleScreenShare,
    connectionState,
}: MeetingRoomShellProps) {
    const [localMicOn, setLocalMicOn] = useState(true);
    const [localCameraOn, setLocalCameraOn] = useState(true);
    const [localScreenSharing, setLocalScreenSharing] = useState(false);
    const isMicOn = isMicOnProp ?? localMicOn;
    const isCameraOn = isCameraOnProp ?? localCameraOn;
    const isScreenSharing = isScreenSharingProp ?? localScreenSharing;
    const handleToggleMic =
        onToggleMic ?? (() => setLocalMicOn((value) => !value));
    const handleToggleCamera =
        onToggleCamera ?? (() => setLocalCameraOn((value) => !value));
    const handleToggleScreenShare =
        onToggleScreenShare ?? (() => setLocalScreenSharing((value) => !value));
    const elapsed = useElapsedTime(meeting?.startedAt);
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
                                connectionState === 'connecting' &&
                                    'bg-amber-500/20 text-amber-300',
                                connectionState === 'error' &&
                                    'bg-red-500/20 text-red-300',
                                connectionState === 'connected' &&
                                    'bg-emerald-500/20 text-emerald-300',
                                (connectionState === 'idle' ||
                                    connectionState === 'disconnected') &&
                                    'bg-white/10 text-slate-300',
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
            <ParticipantVideoGrid
                participants={participants}
                selfAccountId={selfAccountId}
                isSelfMicOn={isMicOn}
            />
            <footer className='flex items-center justify-center gap-3 border-t border-white/8 bg-slate-950 px-3 py-4 sm:gap-5'>
                <ControlButton
                    label={isMicOn ? 'Mute' : 'Unmute'}
                    icon={isMicOn ? 'mic' : 'micOff'}
                    active={isMicOn}
                    onClick={handleToggleMic}
                />
                <ControlButton
                    label={isCameraOn ? 'Stop video' : 'Start video'}
                    icon={isCameraOn ? 'camera' : 'cameraOff'}
                    active={isCameraOn}
                    onClick={handleToggleCamera}
                />
                <ControlButton
                    label={isScreenSharing ? 'Stop sharing' : 'Share screen'}
                    icon='screen'
                    active={isScreenSharing}
                    onClick={handleToggleScreenShare}
                />
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
            </footer>
        </section>
    );
}
