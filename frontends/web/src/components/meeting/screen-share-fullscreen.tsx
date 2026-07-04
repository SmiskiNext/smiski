'use client';

import { useParticipantTracks, VideoTrack } from '@livekit/components-react';
import { Track } from 'livekit-client';
import { SelfView } from './self-view.tsx';

type ScreenShareFullscreenProps = {
    sharerIdentity: string;
};

/**
 * Fullscreen screen share view shown when the host is sharing their screen.
 * Hides all participant video streams and shows only the screen share track
 * filling the entire area, with the local camera as a floating self-view.
 */
export function ScreenShareFullscreen({
    sharerIdentity,
}: ScreenShareFullscreenProps) {
    const screenShareTracks = useParticipantTracks(
        [Track.Source.ScreenShare],
        sharerIdentity,
    );
    const screenShareTrack = screenShareTracks[0];

    return (
        <section className='relative flex-1 overflow-hidden'>
            {screenShareTrack ? (
                <VideoTrack
                    className='h-full w-full object-contain'
                    playsInline
                    trackRef={screenShareTrack}
                />
            ) : (
                <div className='flex h-full items-center justify-center'>
                    <p className='text-sm text-white/60'>
                        Screen share loading...
                    </p>
                </div>
            )}
            <SelfView />
        </section>
    );
}
