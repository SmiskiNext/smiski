/**
 * ParticipantFilmstrip — the compact participant strip shown beside the stage,
 * replacing the equal-sized `ParticipantVideoGrid` whenever one subject is
 * promoted (the Google Meet arrangement: one large stage, everyone else
 * demoted to thumbnails).
 *
 * Scrolls horizontally under the stage on narrow viewports and vertically
 * alongside it from `lg` up, so the stage keeps as much room as possible.
 *
 * Cells arrive already ordered, capped and stripped of whoever holds the stage
 * (`resolveMeetingLayout`), so the strip renders a bounded number of `<video>`
 * elements instead of one per participant in the room, and ends with the same
 * "+N" summary tile the grid uses.
 */
import {
    type GridCell,
    OverflowTile,
    tileMediaProps,
} from './ParticipantVideoGrid';
import { ParticipantVideoTile } from './ParticipantVideoTile';

export interface ParticipantFilmstripProps {
    /** Ordered, capped, stage-excluded cells from `resolveMeetingLayout`. */
    cells: GridCell[];
    selfAccountId: string;
    isSelfMicOn: boolean;
    activeSpeakerId?: string | null;
    pinnedAccountId?: string | null;
    onTogglePin?: (accountId: string) => void;
}

const CELL_CLASS = 'aspect-video w-36 shrink-0 sm:w-40 lg:w-full';

export function ParticipantFilmstrip({
    cells,
    selfAccountId,
    isSelfMicOn,
    activeSpeakerId = null,
    pinnedAccountId = null,
    onTogglePin,
}: ParticipantFilmstripProps) {
    return (
        <div className='flex shrink-0 gap-3 overflow-x-auto pb-1 lg:w-44 lg:flex-col lg:overflow-x-visible lg:overflow-y-auto lg:pb-0'>
            {cells.map((cell) => {
                if (cell.kind === 'overflow') {
                    return (
                        <div key='overflow' className={CELL_CLASS}>
                            <OverflowTile count={cell.count} />
                        </div>
                    );
                }
                // A screen share never appears in the strip: it either holds
                // the stage beside it, or leads the grid instead of this.
                if (cell.kind === 'screenShare') return null;
                const { participant } = cell;
                return (
                    <div key={participant.accountId} className={CELL_CLASS}>
                        <ParticipantVideoTile
                            participant={participant}
                            {...tileMediaProps(
                                participant,
                                selfAccountId,
                                isSelfMicOn,
                                activeSpeakerId,
                            )}
                            isPinned={participant.accountId === pinnedAccountId}
                            onTogglePin={
                                onTogglePin
                                    ? () => onTogglePin(participant.accountId)
                                    : undefined
                            }
                        />
                    </div>
                );
            })}
        </div>
    );
}
