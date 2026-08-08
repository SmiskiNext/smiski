import { useEffect, useRef } from 'react';
import { Avatar, cn, Icon } from '../../../components/ui';
import type { Participant } from '../../../domain';
import type { LiveMeetingParticipant } from '../../../hooks/useLiveKitRoom';

export interface ParticipantVideoTileProps {
    participant: Participant | LiveMeetingParticipant;
    isSelf?: boolean;
    isMicOn?: boolean;
    isCameraOn?: boolean;
    videoTrack?: LiveMeetingParticipant['videoTrack'];
    audioTrack?: LiveMeetingParticipant['audioTrack'];
    /** Draws the active-speaker ring; sourced from LiveKit's audio levels. */
    isSpeaking?: boolean;
    isPinned?: boolean;
    /**
     * Pins or unpins this participant. Absent on tiles that are not pinnable
     * (a backend roster entry rendered before LiveKit connects), which also
     * hides the pin button.
     */
    onTogglePin?: () => void;
}

export function ParticipantVideoTile({
    participant,
    isSelf,
    isMicOn,
    isCameraOn,
    videoTrack,
    audioTrack,
    isSpeaking,
    isPinned,
    onTogglePin,
}: ParticipantVideoTileProps) {
    const videoEl = useRef<HTMLVideoElement>(null);
    const audioEl = useRef<HTMLAudioElement>(null);

    // `<video>` stays mounted regardless of camera on/off (see `showVideo` below,
    // which only toggles CSS visibility) — LiveKit's setCameraEnabled(false)
    // *mutes* the existing track rather than unpublishing it, so re-enabling
    // reuses the SAME track object. If the element were conditionally
    // unmounted, the fresh DOM node created on re-enable would never receive
    // `attach()` again, since this effect only re-runs when the `videoTrack`
    // reference itself changes.
    useEffect(() => {
        if (!videoTrack || !videoEl.current) return;
        const el = videoEl.current;
        videoTrack.attach(el);
        return () => {
            videoTrack.detach(el);
        };
    }, [videoTrack]);

    useEffect(() => {
        if (isSelf || !audioTrack || !audioEl.current) return;
        const el = audioEl.current;
        audioTrack.attach(el);
        return () => {
            audioTrack.detach(el);
        };
    }, [isSelf, audioTrack]);

    const showVideo = Boolean(videoTrack) && isCameraOn !== false;

    return (
        <article
            className={cn(
                'group relative flex h-full w-full items-center justify-center overflow-hidden rounded-2xl border border-white/8 bg-gradient-to-br from-slate-800 to-slate-900 shadow-inner transition-shadow',
                isSpeaking
                    && 'ring-2 ring-emerald-400 shadow-[0_0_16px_rgba(52,211,153,0.45)]',
            )}
        >
            <div className='absolute inset-0 opacity-30 [background-image:radial-gradient(circle_at_30%_20%,rgba(59,130,246,.28),transparent_38%)]' />
            <Avatar
                name={participant.displayName}
                size='xl'
                className='relative ring-4 ring-white/10'
            />
            <video
                ref={videoEl}
                autoPlay
                playsInline
                muted={isSelf}
                className={cn(
                    'absolute inset-0 h-full w-full object-cover',
                    showVideo ? 'opacity-100' : 'pointer-events-none opacity-0',
                )}
            >
                <track kind='captions' />
            </video>
            {!isSelf && (
                <audio ref={audioEl} autoPlay>
                    <track kind='captions' />
                </audio>
            )}
            {participant.role === 'HOST' && (
                <span className='absolute top-3 right-3 rounded-full bg-brand-500/90 px-2 py-1 text-[9px] font-bold tracking-wide text-white uppercase'>
                    Host
                </span>
            )}
            {onTogglePin && (
                <button
                    type='button'
                    aria-label={
                        isPinned
                            ? `Unpin ${participant.displayName}`
                            : `Pin ${participant.displayName}`
                    }
                    aria-pressed={isPinned}
                    onClick={(event) => {
                        event.stopPropagation();
                        onTogglePin();
                    }}
                    className={cn(
                        'absolute top-3 left-3 flex size-7 items-center justify-center rounded-full bg-black/60 text-white transition',
                        'opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 focus-visible:opacity-100',
                        isPinned && 'opacity-100',
                    )}
                >
                    <Icon name={isPinned ? 'pinOff' : 'pin'} size={14} />
                </button>
            )}
            <div className='absolute right-0 bottom-0 left-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/80 to-transparent px-3 pt-8 pb-3 text-white'>
                <span className='flex min-w-0 items-center gap-1.5 truncate text-xs font-semibold'>
                    {isPinned && (
                        <Icon
                            name='pin'
                            size={12}
                            className='shrink-0 text-brand-300'
                        />
                    )}
                    <span className='truncate'>
                        {participant.displayName}
                        {isSelf && ' (You)'}
                    </span>
                </span>
                {isSelf && isMicOn === false && (
                    <span className='flex size-7 items-center justify-center rounded-full bg-red-500/25 text-red-300'>
                        <Icon name='micOff' size={14} />
                    </span>
                )}
            </div>
        </article>
    );
}
