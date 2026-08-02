/**
 * ScreenShareStage — the large "someone is presenting" surface.
 *
 * Uses `object-contain`, not `object-cover` like `ParticipantVideoTile`: a
 * shared screen can be any aspect ratio (an ultrawide monitor, a portrait
 * window), and cropping it would hide exactly the content being presented.
 * The letterboxing that results is the correct trade.
 */
import { useEffect, useRef } from 'react';
import { Icon } from '../../../components/ui';
import type { ScreenShareFeed } from '../../../hooks/useLiveKitRoom';

export interface ScreenShareStageProps {
    feed: ScreenShareFeed;
}

export function ScreenShareStage({ feed }: ScreenShareStageProps) {
    const videoEl = useRef<HTMLVideoElement>(null);

    useEffect(() => {
        const el = videoEl.current;
        if (!el) return;
        feed.track.attach(el);
        return () => {
            feed.track.detach(el);
        };
    }, [feed.track]);

    return (
        <div className='relative flex h-full w-full items-center justify-center overflow-hidden rounded-2xl border border-white/8 bg-black'>
            <video
                ref={videoEl}
                autoPlay
                playsInline
                // Always muted: this element renders the screen's video only —
                // the presenter's voice arrives on their microphone track, and
                // any tab audio would double up against it.
                muted
                className='h-full w-full object-contain'
            >
                <track kind='captions' />
            </video>
            <span className='absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full bg-black/70 px-2.5 py-1 text-[10px] font-bold tracking-wide text-white uppercase'>
                <Icon name='screen' size={12} />
                {feed.isLocal
                    ? 'You are presenting'
                    : `${feed.participantName} is presenting`}
            </span>
        </div>
    );
}
