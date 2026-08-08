/**
 * ParticipantVideoGrid — adaptive, snug-fit grid of ParticipantVideoTile
 * (Google Meet / Zoom style). Tiles are packed into rows (cols ~= sqrt(n),
 * capped at MAX_COLUMNS). Each row's tile size is computed in JS from the
 * measured container box, capped to a 16:9 aspect ratio and centered —
 * this (not CSS alone) is what keeps a lone tile in a short/wide row from
 * stretching to fill the whole row instead of staying a sane video-call
 * size.
 *
 * Cell order, the "+N" overflow cell and the optional leading screen-share
 * cell all arrive already resolved by `resolveMeetingLayout` (pinned first,
 * self last, everyone past the cap collapsed). This component only decides
 * geometry, so the grid and the filmstrip cannot drift apart on those rules.
 *
 * In Tiled mode a live screen share becomes that leading cell instead of
 * taking over a stage: the mode promises equal tiles, but hiding the one thing
 * actually being presented is worse than bending the grid around it.
 */

import { cn, Icon } from '../../../components/ui';
import type { MeetingLayoutCell, Participant } from '../../../domain';
import { useElementSize } from '../../../hooks/useElementSize';
import type {
    LiveMeetingParticipant,
    ScreenShareFeed,
} from '../../../hooks/useLiveKitRoom';
import { ParticipantVideoTile } from './ParticipantVideoTile';
import { ScreenShareStage } from './ScreenShareStage';

export type GridParticipant = Participant | LiveMeetingParticipant;

export type GridCell = MeetingLayoutCell<GridParticipant>;

/** Cells the row packer handles — the screen share is placed on its own. */
type GridTileCell = Exclude<GridCell, { kind: 'screenShare' }>;

/**
 * Only a LiveKit-sourced participant carries media tracks; a roster entry from
 * the meeting store has none. Exported so `ParticipantFilmstrip` narrows the
 * same union the same way instead of re-deriving the rule.
 */
export function isLiveParticipant(
    participant: GridParticipant,
): participant is LiveMeetingParticipant {
    return 'videoTrack' in participant;
}

const MAX_COLUMNS = 4;
const GAP = 12;
const TILE_ASPECT = 16 / 9;
const MIN_CONTAINER_HEIGHT = 320;

/**
 * Height split between the leading screen-share cell and the participant rows
 * under it, as flex-grow units. Expressed as flex rather than pixels so the
 * split holds at both container heights (`h-80` / `lg:h-120`) without the tile
 * math having to agree with the measured box to the pixel.
 */
const SCREEN_CELL_FLEX = 3;
const TILE_ROWS_FLEX = 2;

export interface TileMediaProps {
    isSelf: boolean;
    isMicOn: boolean | undefined;
    isCameraOn: boolean | undefined;
    videoTrack: LiveMeetingParticipant['videoTrack'] | undefined;
    audioTrack: LiveMeetingParticipant['audioTrack'] | undefined;
    isSpeaking: boolean;
}

/**
 * Media props for one tile, resolved against whichever source the participant
 * came from. Exported so `ParticipantFilmstrip` derives them identically
 * rather than repeating the narrowing rules.
 *
 * A roster-only participant has no tracks and no live speaking flag, so its
 * ring falls back to the room's active-speaker id; the local user's mic state
 * comes from the caller, which knows it before LiveKit reports a roster.
 */
export function tileMediaProps(
    participant: GridParticipant,
    selfAccountId: string,
    isSelfMicOn: boolean,
    activeSpeakerId: string | null,
): TileMediaProps {
    const isSelf = participant.accountId === selfAccountId;
    const isLive = isLiveParticipant(participant);
    return {
        isSelf,
        isMicOn: isLive
            ? participant.isMicOn
            : isSelf
              ? isSelfMicOn
              : undefined,
        isCameraOn: isLive ? participant.isCameraOn : undefined,
        videoTrack: isLive ? participant.videoTrack : undefined,
        audioTrack: isLive ? participant.audioTrack : undefined,
        isSpeaking: isLive
            ? participant.isSpeaking
            : participant.accountId === activeSpeakerId,
    };
}

function chunkIntoRows(cells: GridTileCell[]): GridTileCell[][] {
    if (cells.length === 0) return [];
    const cols = Math.min(Math.ceil(Math.sqrt(cells.length)), MAX_COLUMNS);
    const rows: GridTileCell[][] = [];
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

export function OverflowTile({ count }: { count: number }) {
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
    /** Ordered, capped cells from `resolveMeetingLayout`. */
    cells: GridCell[];
    selfAccountId: string;
    isSelfMicOn: boolean;
    /** Rendered as the leading cell when `cells` carries a screen-share cell. */
    screenShare?: ScreenShareFeed | null;
    activeSpeakerId?: string | null;
    pinnedAccountId?: string | null;
    onTogglePin?: (accountId: string) => void;
}

export function ParticipantVideoGrid({
    cells,
    selfAccountId,
    isSelfMicOn,
    screenShare = null,
    activeSpeakerId = null,
    pinnedAccountId = null,
    onTogglePin,
}: ParticipantVideoGridProps) {
    const [containerRef, { width: containerWidth, height: measuredHeight }] =
        useElementSize<HTMLDivElement>();
    const containerHeight = Math.max(measuredHeight, MIN_CONTAINER_HEIGHT);

    const hasScreenCell =
        screenShare !== null
        && cells.some((cell) => cell.kind === 'screenShare');
    const tileCells = cells.filter(
        (cell): cell is GridTileCell => cell.kind !== 'screenShare',
    );
    const rows = chunkIntoRows(tileCells);
    const rowsHeight = hasScreenCell
        ? ((containerHeight - GAP) * TILE_ROWS_FLEX)
          / (SCREEN_CELL_FLEX + TILE_ROWS_FLEX)
        : containerHeight;

    return (
        <div
            ref={containerRef}
            className='flex h-80 flex-col justify-center gap-3 overflow-hidden bg-slate-900 p-3 sm:p-4 lg:h-120'
        >
            {hasScreenCell && screenShare && (
                <div
                    className='min-h-0 min-w-0'
                    style={{ flex: `${SCREEN_CELL_FLEX} 1 0%` }}
                >
                    <ScreenShareStage feed={screenShare} />
                </div>
            )}
            {rows.length > 0 && (
                <div
                    className={cn(
                        'flex min-h-0 flex-col justify-center gap-3',
                        !hasScreenCell && 'flex-1',
                    )}
                    style={
                        hasScreenCell
                            ? { flex: `${TILE_ROWS_FLEX} 1 0%` }
                            : undefined
                    }
                >
                    {rows.map((row, rowIndex) => {
                        const tileSize = containerWidth
                            ? computeTileSize(
                                  containerWidth,
                                  rowsHeight,
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
                                        : {
                                              width: '100%',
                                              aspectRatio: TILE_ASPECT,
                                          };
                                    if (cell.kind === 'overflow') {
                                        return (
                                            <div
                                                key='overflow'
                                                style={style}
                                                className='shrink-0'
                                            >
                                                <OverflowTile
                                                    count={cell.count}
                                                />
                                            </div>
                                        );
                                    }
                                    const { participant } = cell;
                                    return (
                                        <div
                                            key={participant.accountId}
                                            style={style}
                                            className='shrink-0'
                                        >
                                            <ParticipantVideoTile
                                                participant={participant}
                                                {...tileMediaProps(
                                                    participant,
                                                    selfAccountId,
                                                    isSelfMicOn,
                                                    activeSpeakerId,
                                                )}
                                                isPinned={
                                                    participant.accountId
                                                    === pinnedAccountId
                                                }
                                                onTogglePin={
                                                    onTogglePin
                                                        ? () =>
                                                              onTogglePin(
                                                                  participant.accountId,
                                                              )
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
            )}
        </div>
    );
}
