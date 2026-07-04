'use client';

import {
    AudioTrack,
    useLocalParticipant,
    useRemoteParticipants,
} from '@livekit/components-react';
import { Track } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Avatar, AvatarFallback } from '@/components/ui/avatar.tsx';
import { ParticipantTile } from './participant-tile.tsx';
import type { ParticipantViewModel } from './types.ts';

const TILES_PER_PAGE = 4;

function buildViewModel(
    participant:
        | ReturnType<typeof useLocalParticipant>['localParticipant']
        | ReturnType<typeof useRemoteParticipants>[number],
    isLocal: boolean,
): ParticipantViewModel {
    return {
        identity: participant.identity,
        displayName: participant.name ?? participant.identity,
        isMicEnabled: participant.isMicrophoneEnabled,
        isCameraEnabled: participant.isCameraEnabled,
        isLocal,
        livekitParticipant: participant,
    };
}

function RemoteAudio() {
    const remoteParticipants = useRemoteParticipants();
    return (
        <>
            {remoteParticipants.map((p) => {
                const audioTracks = p
                    .getTrackPublications()
                    .filter(
                        (pub) =>
                            pub.source === Track.Source.Microphone && pub.track,
                    );
                return audioTracks.map((pub) => (
                    <AudioTrack
                        key={`${p.identity}-audio`}
                        trackRef={{
                            participant: p,
                            source: Track.Source.Microphone,
                            publication: pub,
                        }}
                    />
                ));
            })}
        </>
    );
}

function PageIndicator({
    pageCount,
    activePage,
    onPageClick,
}: {
    pageCount: number;
    activePage: number;
    onPageClick: (page: number) => void;
}) {
    if (pageCount <= 1) return null;

    return (
        <nav
            aria-label='Grid pages'
            className='flex items-center justify-center gap-1.5 py-2'
        >
            {Array.from({ length: pageCount }, (_, i) => (
                <button
                    aria-current={i === activePage ? 'page' : undefined}
                    aria-label={`Page ${i + 1}`}
                    className={`h-1.5 rounded-full transition-all ${
                        i === activePage
                            ? 'w-4 bg-primary'
                            : 'w-1.5 bg-white/40 hover:bg-white/60'
                    }`}
                    key={`page-dot-${i}`}
                    onClick={() => onPageClick(i)}
                    type='button'
                />
            ))}
        </nav>
    );
}

function PagedGrid({ viewModels }: { viewModels: ParticipantViewModel[] }) {
    const scrollRef = useRef<HTMLDivElement>(null);
    const [activePage, setActivePage] = useState(0);
    const pageCount = Math.ceil(viewModels.length / TILES_PER_PAGE);

    const pages = Array.from({ length: pageCount }, (_, i) =>
        viewModels.slice(i * TILES_PER_PAGE, (i + 1) * TILES_PER_PAGE),
    );

    const handleScroll = useCallback(() => {
        const el = scrollRef.current;
        if (!el) return;
        const pageWidth = el.clientWidth;
        if (pageWidth === 0) return;
        const page = Math.round(el.scrollLeft / pageWidth);
        setActivePage(Math.min(page, pageCount - 1));
    }, [pageCount]);

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        el.addEventListener('scroll', handleScroll, { passive: true });
        return () => el.removeEventListener('scroll', handleScroll);
    }, [handleScroll]);

    const scrollToPage = useCallback((page: number) => {
        const el = scrollRef.current;
        if (!el) return;
        el.scrollTo({ left: page * el.clientWidth, behavior: 'smooth' });
    }, []);

    return (
        <div className='flex h-full flex-col'>
            <div
                className='flex flex-1 snap-x snap-mandatory overflow-x-auto scrollbar-none'
                ref={scrollRef}
            >
                {pages.map((pageTiles, pageIndex) => (
                    <div
                        className='grid h-full w-full flex-shrink-0 snap-center grid-cols-2 grid-rows-2 gap-3'
                        key={`page-${pageIndex}`}
                    >
                        {pageTiles.map((vm) => (
                            <ParticipantTile
                                key={vm.identity}
                                participant={vm}
                            />
                        ))}
                    </div>
                ))}
            </div>
            <PageIndicator
                activePage={activePage}
                onPageClick={scrollToPage}
                pageCount={pageCount}
            />
        </div>
    );
}

function SmallGrid({ viewModels }: { viewModels: ParticipantViewModel[] }) {
    const count = viewModels.length;
    const gridClass = count <= 2 ? 'grid-cols-1' : 'grid-cols-2';

    return (
        <div className={`grid h-full ${gridClass} gap-3`}>
            {viewModels.map((vm) => (
                <ParticipantTile key={vm.identity} participant={vm} />
            ))}
        </div>
    );
}

/**
 * Root participant grid rendering all participants (including local) in a
 * fixed grid. When total <= 4: responsive grid (1 col for 2, 2 cols for 3-4).
 * When total > 4: paged 2x2 grid with horizontal scrolling and page indicator.
 */
export function ParticipantGrid() {
    const { localParticipant } = useLocalParticipant();
    const remoteParticipants = useRemoteParticipants();

    const allViewModels: ParticipantViewModel[] = [
        buildViewModel(localParticipant, true),
        ...remoteParticipants.map((p) => buildViewModel(p, false)),
    ];

    const showEmptyState = remoteParticipants.length === 0;

    return (
        <section className='relative flex-1 overflow-hidden p-4'>
            <RemoteAudio />

            {showEmptyState ? (
                <div className='flex h-full items-center justify-center'>
                    <div className='flex h-48 w-48 items-center justify-center rounded-full bg-[linear-gradient(160deg,var(--tile-bg-navy-start)_0%,var(--tile-bg-navy-mid)_50%,var(--tile-bg-navy-end)_100%)]'>
                        <Avatar className='h-full w-full bg-transparent'>
                            <AvatarFallback className='bg-transparent text-xl font-semibold text-white/60'>
                                {(
                                    localParticipant.name
                                    ?? localParticipant.identity
                                )
                                    .split(' ')
                                    .map((p) => p[0])
                                    .join('')
                                    .slice(0, 2)
                                    .toUpperCase()}
                            </AvatarFallback>
                        </Avatar>
                    </div>
                </div>
            ) : allViewModels.length <= TILES_PER_PAGE ? (
                <SmallGrid viewModels={allViewModels} />
            ) : (
                <PagedGrid viewModels={allViewModels} />
            )}
        </section>
    );
}
