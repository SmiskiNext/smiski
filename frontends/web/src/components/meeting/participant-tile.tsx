'use client';

import {
    useIsSpeaking,
    useParticipantTracks,
    VideoTrack,
} from '@livekit/components-react';
import { Track } from 'livekit-client';
import { Mic, MicOff, Volume2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import {
    Avatar,
    AvatarFallback,
    AvatarImage,
} from '@/components/ui/avatar.tsx';
import { cn } from '@/lib/utils.ts';
import type { ParticipantViewModel } from './types.ts';

type ParticipantTileProps = {
    participant: ParticipantViewModel;
    isPromoted?: boolean;
};

function InitialsAvatar({
    name,
    avatarUrl,
}: {
    name: string;
    avatarUrl?: string;
}) {
    const initials = name
        .split(' ')
        .map((part) => part[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();

    return (
        <div className='absolute inset-0 flex items-center justify-center bg-[linear-gradient(160deg,var(--tile-bg-navy-start)_0%,var(--tile-bg-navy-mid)_50%,var(--tile-bg-navy-end)_100%)]'>
            <Avatar className='h-20 w-20 shadow-[0_14px_32px_-18px_rgba(0,0,0,0.6)] md:h-28 md:w-28'>
                {avatarUrl && <AvatarImage alt={name} src={avatarUrl} />}
                <AvatarFallback className='bg-gradient-to-br from-[var(--avatar-gradient-navy-start)] to-[var(--avatar-gradient-navy-end)] text-2xl font-semibold text-white md:text-3xl'>
                    {initials}
                </AvatarFallback>
            </Avatar>
        </div>
    );
}

/**
 * Renders a single participant tile with live video, camera-off fallback,
 * name overlay, mic state, and speaking indicator.
 */
export function ParticipantTile({
    participant,
    isPromoted = false,
}: ParticipantTileProps) {
    const t = useTranslations('meetingRoom');
    const isSpeaking = useIsSpeaking(participant.livekitParticipant);
    const cameraTracks = useParticipantTracks(
        [Track.Source.Camera],
        participant.identity,
    );
    const cameraTrack = cameraTracks[0];
    const screenShareTracks = useParticipantTracks(
        [Track.Source.ScreenShare],
        participant.identity,
    );
    const screenShareTrack = screenShareTracks[0];
    const videoTrack = screenShareTrack ?? cameraTrack;

    const hasVideoTrack = Boolean(
        screenShareTrack || (cameraTrack && participant.isCameraEnabled),
    );

    return (
        <div
            className={cn(
                'relative overflow-hidden rounded-[1.4rem] bg-[linear-gradient(160deg,var(--tile-bg-charcoal-start)_0%,var(--tile-bg-charcoal-mid)_50%,var(--tile-bg-charcoal-end)_100%)]',
                isSpeaking
                    && 'ring-[3px] ring-primary ring-offset-2 ring-offset-meeting-bg',
                isPromoted && 'h-full w-full',
            )}
        >
            {hasVideoTrack && videoTrack ? (
                <VideoTrack
                    className={cn(
                        'absolute inset-0 h-full w-full',
                        screenShareTrack ? 'object-contain' : 'object-cover',
                    )}
                    playsInline
                    trackRef={videoTrack}
                />
            ) : (
                <InitialsAvatar
                    avatarUrl={participant.avatarUrl}
                    name={participant.displayName}
                />
            )}

            {isSpeaking && (
                <div className='absolute right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full bg-primary shadow-[0_8px_20px_-8px_rgba(26,115,232,0.8)]'>
                    <Volume2 className='h-4 w-4 text-white' />
                </div>
            )}

            <div className='absolute bottom-4 left-4 flex items-center gap-2 rounded-xl bg-black/55 px-3 py-1.5 backdrop-blur-sm'>
                <span
                    className={
                        participant.isMicEnabled
                            ? 'text-white/90'
                            : 'text-red-400'
                    }
                >
                    {participant.isMicEnabled ? (
                        <Mic className='h-4 w-4' />
                    ) : (
                        <MicOff className='h-4 w-4' />
                    )}
                </span>
                <span className='text-sm font-medium text-white'>
                    {participant.displayName}
                    {participant.isLocal ? ` (${t('you')})` : ''}
                </span>
            </div>
        </div>
    );
}
