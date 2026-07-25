/**
 * ParticipantVideoGrid — adaptive, snug-fit grid of ParticipantVideoTile
 * (Google Meet / Zoom style). Tiles are packed into rows (cols ~= sqrt(n),
 * capped at MAX_COLUMNS). Each row's tile size is computed in JS from the
 * measured container box, capped to a 16:9 aspect ratio and centered —
 * this (not CSS alone) is what keeps a lone tile in a short/wide row from
 * stretching to fill the whole row instead of staying a sane video-call
 * size. The self tile is always ordered last, so it (like any participant
 * landing alone in a trailing row) ends up a bit bigger than a full row's
 * tiles rather than looking orphaned. Past MAX_VISIBLE_TILES, the extra
 * participants collapse into a single "+N" summary tile.
 */

import { Icon } from '../../../components/ui';
import type { Participant } from '../../../domain';
import { useElementSize } from '../../../hooks/useElementSize';
import type { LiveMeetingParticipant } from '../../../hooks/useLiveKitRoom';
import { ParticipantVideoTile } from './ParticipantVideoTile';

type GridParticipant = Participant | LiveMeetingParticipant;

type GridCell =
    | { kind: 'participant'; participant: GridParticipant }
    | { kind: 'overflow'; count: number };

function isLiveParticipant(
    participant: GridParticipant,
): participant is LiveMeetingParticipant {
    return 'videoTrack' in participant;
}

const MAX_VISIBLE_TILES = 8;
const MAX_COLUMNS = 4;
const GAP = 12;
const TILE_ASPECT = 16 / 9;
const MIN_CONTAINER_HEIGHT = 320;

function chunkIntoRows(cells: GridCell[]): GridCell[][] {
    if (cells.length === 0) return [];
    const cols = Math.min(Math.ceil(Math.sqrt(cells.length)), MAX_COLUMNS);
    const rows: GridCell[][] = [];
    for (let i = 0; i < cells.length; i += cols) {
        rows.push(cells.slice(i, i + cols));
    }
    return rows;
}

/**
 * Largest 16:9 tile size that fits `itemsInRow` tiles across the container
 * width AND `rowCount` rows down the container height — whichever axis is
 * tighter wins, so a tile never stretches past a sane aspect ratio.
 */
function computeTileSize(
    containerWidth: number,
    containerHeight: number,
    itemsInRow: number,
    rowCount: number,
) {
    const widthBudget = (containerWidth - GAP * (itemsInRow - 1)) / itemsInRow;
    const heightBudget = (containerHeight - GAP * (rowCount - 1)) / rowCount;
    const heightFromWidth = widthBudget / TILE_ASPECT;
    if (heightFromWidth <= heightBudget) {
        return { width: widthBudget, height: heightFromWidth };
    }
    return { width: heightBudget * TILE_ASPECT, height: heightBudget };
}

function OverflowTile({ count }: { count: number }) {
    return (
        <div className='flex h-full w-full flex-col items-center justify-center gap-1 rounded-2xl border border-white/8 bg-slate-800 text-white'>
            <Icon name='people' size={20} className='text-slate-300' />
            <span className='text-lg font-bold'>+{count}</span>
            <span className='text-xs text-slate-400'>
                {count === 1 ? 'more person' : 'more people'}
            </span>
        </div>
    );
}

export interface ParticipantVideoGridProps {
    participants: GridParticipant[];
    selfAccountId: string;
    isSelfMicOn: boolean;
}

export function ParticipantVideoGrid({
    participants,
    selfAccountId,
    isSelfMicOn,
}: ParticipantVideoGridProps) {
    const [containerRef, { width: containerWidth, height: measuredHeight }] =
        useElementSize<HTMLDivElement>();
    const containerHeight = Math.max(measuredHeight, MIN_CONTAINER_HEIGHT);

    const self = participants.find(
        (participant) => participant.accountId === selfAccountId,
    );
    const others = participants.filter(
        (participant) => participant.accountId !== selfAccountId,
    );

    const otherSlots = MAX_VISIBLE_TILES - (self ? 1 : 0);
    const overflowCount = Math.max(0, others.length - otherSlots);
    const visibleOthers =
        overflowCount > 0 ? others.slice(0, otherSlots - 1) : others;

    const cells: GridCell[] = [
        ...visibleOthers.map(
            (participant): GridCell => ({
                kind: 'participant',
                participant,
            }),
        ),
        ...(overflowCount > 0
            ? [{ kind: 'overflow', count: overflowCount } as const]
            : []),
        ...(self ? [{ kind: 'participant', participant: self } as const] : []),
    ];

    const rows = chunkIntoRows(cells);

    return (
        <div
            ref={containerRef}
            className='flex h-80 flex-col justify-center gap-3 overflow-hidden bg-slate-900 p-3 sm:p-4 lg:h-120'
        >
            {rows.map((row, rowIndex) => {
                const tileSize = containerWidth
                    ? computeTileSize(
                          containerWidth,
                          containerHeight,
                          row.length,
                          rows.length,
                      )
                    : null;
                return (
                    <div
                        key={rowIndex}
                        className='flex flex-1 items-center justify-center gap-3'
                    >
                        {row.map((cell) => {
                            const style = tileSize
                                ? {
                                      width: tileSize.width,
                                      height: tileSize.height,
                                  }
                                : { width: '100%', aspectRatio: TILE_ASPECT };
                            return cell.kind === 'overflow' ? (
                                <div
                                    key='overflow'
                                    style={style}
                                    className='shrink-0'
                                >
                                    <OverflowTile count={cell.count} />
                                </div>
                            ) : (
                                <div
                                    key={cell.participant.accountId}
                                    style={style}
                                    className='shrink-0'
                                >
                                    <ParticipantVideoTile
                                        participant={cell.participant}
                                        isSelf={
                                            cell.participant.accountId
                                            === selfAccountId
                                        }
                                        isMicOn={
                                            isLiveParticipant(cell.participant)
                                                ? cell.participant.isMicOn
                                                : cell.participant.accountId
                                                    === selfAccountId
                                                  ? isSelfMicOn
                                                  : undefined
                                        }
                                        isCameraOn={
                                            isLiveParticipant(cell.participant)
                                                ? cell.participant.isCameraOn
                                                : undefined
                                        }
                                        videoTrack={
                                            isLiveParticipant(cell.participant)
                                                ? cell.participant.videoTrack
                                                : undefined
                                        }
                                        audioTrack={
                                            isLiveParticipant(cell.participant)
                                                ? cell.participant.audioTrack
                                                : undefined
                                        }
                                    />
                                </div>
                            );
                        })}
                    </div>
                );
            })}
        </div>
    );
}
