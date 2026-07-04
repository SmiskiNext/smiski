'use client';

import { useLocalParticipant, VideoTrack } from '@livekit/components-react';
import { Track } from 'livekit-client';
import { useTranslations } from 'next-intl';
import {
    type PointerEvent as ReactPointerEvent,
    useCallback,
    useEffect,
    useRef,
    useState,
} from 'react';
import { Avatar, AvatarFallback } from '@/components/ui/avatar.tsx';

const PREVIEW_WIDTH = 192;
const PREVIEW_HEIGHT = 112;
const EDGE_PADDING = 12;

type Position = {
    x: number;
    y: number;
};

function getInitials(name: string): string {
    return name
        .split(' ')
        .map((part) => part[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();
}

function clampPosition(
    next: Position,
    container: HTMLElement | null,
): Position {
    if (!container) return next;
    const maxX = Math.max(
        EDGE_PADDING,
        container.clientWidth - PREVIEW_WIDTH - EDGE_PADDING,
    );
    const maxY = Math.max(
        EDGE_PADDING,
        container.clientHeight - PREVIEW_HEIGHT - EDGE_PADDING,
    );
    return {
        x: Math.min(Math.max(next.x, EDGE_PADDING), maxX),
        y: Math.min(Math.max(next.y, EDGE_PADDING), maxY),
    };
}

function SelfInitialsAvatar({ initials }: { initials: string }) {
    return (
        <div className='flex h-full w-full items-center justify-center bg-[linear-gradient(160deg,var(--tile-bg-navy-start)_0%,var(--tile-bg-navy-mid)_50%,var(--tile-bg-navy-end)_100%)]'>
            <Avatar className='h-12 w-12'>
                <AvatarFallback className='bg-gradient-to-br from-[var(--avatar-gradient-navy-start)] to-[var(--avatar-gradient-navy-end)] text-base font-semibold text-white'>
                    {initials}
                </AvatarFallback>
            </Avatar>
        </div>
    );
}

/**
 * Floating self-view preview showing local camera video when enabled
 * or an initials fallback when the camera is off.
 *
 * The preview is draggable within its offset parent. The local participant's
 * initials avatar is always shown in the top-left corner so the user remains
 * identifiable even while the camera is streaming.
 */
export function SelfView() {
    const t = useTranslations('meetingRoom');
    const { localParticipant, isCameraEnabled, cameraTrack } =
        useLocalParticipant();

    const trackRef = cameraTrack
        ? {
              participant: localParticipant,
              source: Track.Source.Camera,
              publication: cameraTrack,
          }
        : null;

    const displayName = localParticipant.name ?? localParticipant.identity;
    const initials = getInitials(displayName);

    const previewRef = useRef<HTMLElement | null>(null);
    const dragOffsetRef = useRef<Position>({ x: 0, y: 0 });
    const [position, setPosition] = useState<Position | null>(null);
    const [isDragging, setIsDragging] = useState(false);

    useEffect(() => {
        function handleResize() {
            const container = previewRef.current
                ?.offsetParent as HTMLElement | null;
            setPosition((prev) =>
                prev ? clampPosition(prev, container) : prev,
            );
        }

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    const handlePointerDown = useCallback(
        (event: ReactPointerEvent<HTMLElement>) => {
            const node = previewRef.current;
            if (!node) return;
            const rect = node.getBoundingClientRect();
            const container = node.offsetParent as HTMLElement | null;
            const containerRect = container?.getBoundingClientRect();
            const baseX = containerRect?.left ?? 0;
            const baseY = containerRect?.top ?? 0;

            dragOffsetRef.current = {
                x: event.clientX - rect.left,
                y: event.clientY - rect.top,
            };
            setPosition(
                clampPosition(
                    { x: rect.left - baseX, y: rect.top - baseY },
                    container,
                ),
            );
            setIsDragging(true);
            node.setPointerCapture(event.pointerId);
        },
        [],
    );

    const handlePointerMove = useCallback(
        (event: ReactPointerEvent<HTMLElement>) => {
            if (!isDragging) return;
            const node = previewRef.current;
            if (!node) return;
            const container = node.offsetParent as HTMLElement | null;
            const containerRect = container?.getBoundingClientRect();
            const baseX = containerRect?.left ?? 0;
            const baseY = containerRect?.top ?? 0;

            const next: Position = {
                x: event.clientX - baseX - dragOffsetRef.current.x,
                y: event.clientY - baseY - dragOffsetRef.current.y,
            };
            setPosition(clampPosition(next, container));
        },
        [isDragging],
    );

    const handlePointerEnd = useCallback(
        (event: ReactPointerEvent<HTMLElement>) => {
            if (!isDragging) return;
            const node = previewRef.current;
            if (node?.hasPointerCapture(event.pointerId)) {
                node.releasePointerCapture(event.pointerId);
            }
            setIsDragging(false);
        },
        [isDragging],
    );

    const positionStyle =
        position === null
            ? { right: '1.25rem', bottom: '1.25rem' }
            : { left: `${position.x}px`, top: `${position.y}px` };

    return (
        <section
            aria-label={t('selfViewLabel')}
            className={`absolute z-10 h-28 w-48 select-none touch-none overflow-hidden rounded-2xl border border-border/40 bg-meeting-bg shadow-[0_8px_24px_-8px_rgba(0,0,0,0.5)] ${
                isDragging ? 'cursor-grabbing' : 'cursor-grab'
            }`}
            onPointerCancel={handlePointerEnd}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerEnd}
            ref={previewRef}
            style={positionStyle}
        >
            {isCameraEnabled && trackRef && cameraTrack?.track ? (
                <VideoTrack
                    className='h-full w-full object-cover'
                    playsInline
                    trackRef={trackRef}
                />
            ) : (
                <SelfInitialsAvatar initials={initials} />
            )}

            <div className='pointer-events-none absolute left-2 top-2'>
                <Avatar className='h-7 w-7 ring-2 ring-black/40'>
                    <AvatarFallback className='bg-gradient-to-br from-[var(--avatar-gradient-navy-start)] to-[var(--avatar-gradient-navy-end)] text-xs font-semibold text-white'>
                        {initials}
                    </AvatarFallback>
                </Avatar>
            </div>

            <div className='pointer-events-none absolute bottom-1.5 left-2 rounded bg-black/50 px-1.5 py-0.5'>
                <span className='text-xs font-medium text-white'>
                    {t('you')}
                </span>
            </div>
        </section>
    );
}
