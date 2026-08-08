/**
 * Meeting layout policy — decides how the meeting room arranges its video
 * surface, in one pure place the UI can render without re-deriving any rules.
 *
 * Three modes, mirroring Google Meet:
 *
 * - `auto` follows the room: a pin or a live screen share promotes one subject
 *   to a large stage with everyone else in a filmstrip, and anything else is
 *   an equal-sized grid. Speech alone deliberately does NOT promote anyone —
 *   the active speaker changes every few seconds in a real conversation, and
 *   re-arranging the whole surface that often is unusable.
 * - `tiled` is always the equal grid. A screen share still cannot be hidden,
 *   so it joins the grid as one large leading cell instead of taking a stage.
 * - `spotlight` is always the stage, falling back down a subject chain so it
 *   has something to show even when nobody is pinned or presenting.
 *
 * Stage subject priority is fixed across every mode: pinned beats a screen
 * share (an explicit user choice outranks an implicit one), which beats the
 * active speaker, which beats the first participant.
 *
 * Kept free of LiveKit and React types on purpose — participants are only
 * required to carry an `accountId`, and a screen share enters as a boolean, so
 * the caller keeps ownership of the actual media objects.
 */

export type LayoutMode = 'auto' | 'tiled' | 'spotlight';

/** Every mode, in the order the layout menu offers them. */
export const LAYOUT_MODES: readonly LayoutMode[] = [
    'auto',
    'tiled',
    'spotlight',
];

export function isLayoutMode(value: unknown): value is LayoutMode {
    return LAYOUT_MODES.includes(value as LayoutMode);
}

/** Cells the equal grid shows before collapsing the rest into "+N". */
export const GRID_CELL_CAP = 8;

/**
 * Cells the stage's filmstrip shows before collapsing the rest into "+N".
 * Lower than the grid's cap because the strip is a single scrolling column
 * beside the stage, not the whole surface.
 */
export const FILMSTRIP_CELL_CAP = 6;

/** The only participant field this module reads. */
export interface LayoutParticipantLike {
    accountId: string;
}

export interface ResolveMeetingLayoutInput<P extends LayoutParticipantLike> {
    mode: LayoutMode;
    participants: P[];
    selfAccountId: string;
    /** `null` when nothing is pinned; a stale id resolves as unpinned. */
    pinnedAccountId: string | null;
    /** Whether anyone in the room is presenting a screen right now. */
    hasScreenShare: boolean;
    activeSpeakerId: string | null;
    gridCap?: number;
    filmstripCap?: number;
}

export type MeetingLayoutCell<P extends LayoutParticipantLike> =
    | { kind: 'participant'; participant: P }
    | { kind: 'screenShare' }
    | { kind: 'overflow'; count: number };

export type MeetingStageSubject<P extends LayoutParticipantLike> =
    | { kind: 'screenShare' }
    | { kind: 'participant'; participant: P };

export type MeetingLayout<P extends LayoutParticipantLike> =
    | {
          arrangement: 'grid';
          cells: MeetingLayoutCell<P>[];
          overflowCount: number;
      }
    | {
          arrangement: 'stage';
          subject: MeetingStageSubject<P>;
          cells: MeetingLayoutCell<P>[];
          overflowCount: number;
      };

function findParticipant<P extends LayoutParticipantLike>(
    participants: P[],
    accountId: string | null,
): P | undefined {
    return accountId
        ? participants.find(
              (participant) => participant.accountId === accountId,
          )
        : undefined;
}

/**
 * Orders one cell list and caps it.
 *
 * Ordering is pinned-first (the explicit choice stays visible), self-last
 * (a participant alone in the trailing row reads as deliberate rather than
 * orphaned), everyone else in between. Self and the pinned participant are
 * never the ones collapsed away, and the "+N" cell occupies a slot of its own
 * so the cap still describes the rendered cell count.
 */
function buildCells<P extends LayoutParticipantLike>(
    participants: P[],
    selfAccountId: string,
    pinnedAccountId: string | null,
    cap: number,
    leadingScreenShare: boolean,
): { cells: MeetingLayoutCell<P>[]; overflowCount: number } {
    const self = findParticipant(participants, selfAccountId);
    const pinned = findParticipant(participants, pinnedAccountId);
    const isReserved = (participant: P) =>
        participant.accountId === self?.accountId
        || participant.accountId === pinned?.accountId;
    const others = participants.filter(
        (participant) => !isReserved(participant),
    );

    const reservedSlots = (self ? 1 : 0) + (pinned && pinned !== self ? 1 : 0);
    const otherSlots = Math.max(0, cap - reservedSlots);
    const visibleOthers =
        others.length <= otherSlots
            ? others
            : others.slice(0, Math.max(0, otherSlots - 1));
    const overflowCount = others.length - visibleOthers.length;

    const toCell = (participant: P): MeetingLayoutCell<P> => ({
        kind: 'participant',
        participant,
    });

    return {
        cells: [
            ...(leadingScreenShare
                ? [{ kind: 'screenShare' } as MeetingLayoutCell<P>]
                : []),
            ...(pinned && pinned !== self ? [toCell(pinned)] : []),
            ...visibleOthers.map(toCell),
            ...(overflowCount > 0
                ? [{ kind: 'overflow', count: overflowCount } as const]
                : []),
            ...(self ? [toCell(self)] : []),
        ],
        overflowCount,
    };
}

function resolveStageSubject<P extends LayoutParticipantLike>(
    participants: P[],
    pinned: P | undefined,
    hasScreenShare: boolean,
    activeSpeakerId: string | null,
): MeetingStageSubject<P> | null {
    if (pinned) return { kind: 'participant', participant: pinned };
    if (hasScreenShare) return { kind: 'screenShare' };
    const speaker = findParticipant(participants, activeSpeakerId);
    const subject = speaker ?? participants[0];
    return subject ? { kind: 'participant', participant: subject } : null;
}

/**
 * A pin only means something while its participant is still in the room —
 * once they leave, the pin has to be dropped or the stage would keep an empty
 * subject (and `auto` would stay stuck on the stage arrangement). Returns the
 * id to keep, so callers can store the result straight back into state.
 */
export function reconcilePinnedAccountId(
    pinnedAccountId: string | null,
    participants: LayoutParticipantLike[],
): string | null {
    return findParticipant(participants, pinnedAccountId)
        ? pinnedAccountId
        : null;
}

export function resolveMeetingLayout<P extends LayoutParticipantLike>({
    mode,
    participants,
    selfAccountId,
    pinnedAccountId,
    hasScreenShare,
    activeSpeakerId,
    gridCap = GRID_CELL_CAP,
    filmstripCap = FILMSTRIP_CELL_CAP,
}: ResolveMeetingLayoutInput<P>): MeetingLayout<P> {
    const pinned = findParticipant(participants, pinnedAccountId);
    const wantsStage =
        mode === 'spotlight'
        || (mode === 'auto' && (Boolean(pinned) || hasScreenShare));

    if (wantsStage) {
        const subject = resolveStageSubject(
            participants,
            pinned,
            hasScreenShare,
            activeSpeakerId,
        );
        // A stage with nothing to put on it (an empty room in `spotlight`)
        // degrades to the grid rather than rendering a hole.
        if (subject) {
            const filmstripParticipants =
                subject.kind === 'participant'
                    ? participants.filter(
                          (participant) =>
                              participant.accountId
                              !== subject.participant.accountId,
                      )
                    : participants;
            return {
                arrangement: 'stage',
                subject,
                ...buildCells(
                    filmstripParticipants,
                    selfAccountId,
                    pinnedAccountId,
                    filmstripCap,
                    false,
                ),
            };
        }
    }

    return {
        arrangement: 'grid',
        ...buildCells(
            participants,
            selfAccountId,
            pinnedAccountId,
            gridCap,
            hasScreenShare,
        ),
    };
}
