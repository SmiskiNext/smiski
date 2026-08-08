import { describe, expect, it } from 'vitest';
import {
    FILMSTRIP_CELL_CAP,
    GRID_CELL_CAP,
    isLayoutMode,
    type LayoutMode,
    type MeetingLayout,
    reconcilePinnedAccountId,
    resolveMeetingLayout,
} from './meetingLayout';

interface TestParticipant {
    accountId: string;
}

const SELF = 'account-self';

function participants(...accountIds: string[]): TestParticipant[] {
    return accountIds.map((accountId) => ({ accountId }));
}

function resolve(
    overrides: Partial<
        Parameters<typeof resolveMeetingLayout<TestParticipant>>[0]
    > = {},
): MeetingLayout<TestParticipant> {
    return resolveMeetingLayout<TestParticipant>({
        mode: 'auto',
        participants: participants(SELF, 'account-a', 'account-b'),
        selfAccountId: SELF,
        pinnedAccountId: null,
        hasScreenShare: false,
        activeSpeakerId: null,
        ...overrides,
    });
}

function cellIds(layout: MeetingLayout<TestParticipant>): string[] {
    return layout.cells.map((cell) =>
        cell.kind === 'participant'
            ? cell.participant.accountId
            : cell.kind === 'overflow'
              ? `+${cell.count}`
              : 'screenShare',
    );
}

function subjectId(layout: MeetingLayout<TestParticipant>): string {
    if (layout.arrangement !== 'stage') throw new Error('not a stage layout');
    return layout.subject.kind === 'participant'
        ? layout.subject.participant.accountId
        : 'screenShare';
}

describe('isLayoutMode', () => {
    it.each(['auto', 'tiled', 'spotlight'] as LayoutMode[])(
        'accepts %s',
        (mode) => {
            expect(isLayoutMode(mode)).toBe(true);
        },
    );

    it.each([null, undefined, '', 'grid', 42])('rejects %s', (value) => {
        expect(isLayoutMode(value)).toBe(false);
    });
});

describe('resolveMeetingLayout — auto mode', () => {
    it('uses the equal grid while nothing is pinned or presented', () => {
        const layout = resolve({ mode: 'auto' });

        expect(layout.arrangement).toBe('grid');
        expect(cellIds(layout)).toEqual(['account-a', 'account-b', SELF]);
    });

    it('stays on the grid when someone is merely speaking', () => {
        const layout = resolve({ mode: 'auto', activeSpeakerId: 'account-a' });

        expect(layout.arrangement).toBe('grid');
    });

    it('switches to the stage for an active screen share', () => {
        const layout = resolve({ mode: 'auto', hasScreenShare: true });

        expect(subjectId(layout)).toBe('screenShare');
    });

    it('switches to the stage for a pinned participant', () => {
        const layout = resolve({ mode: 'auto', pinnedAccountId: 'account-b' });

        expect(subjectId(layout)).toBe('account-b');
    });
});

describe('resolveMeetingLayout — tiled mode', () => {
    it('keeps the grid even while a screen is shared', () => {
        const layout = resolve({ mode: 'tiled', hasScreenShare: true });

        expect(layout.arrangement).toBe('grid');
    });

    it('inserts the shared screen as the leading grid cell', () => {
        const layout = resolve({ mode: 'tiled', hasScreenShare: true });

        expect(cellIds(layout)).toEqual([
            'screenShare',
            'account-a',
            'account-b',
            SELF,
        ]);
    });

    it('keeps the grid even for a pinned participant, ordering them first', () => {
        const layout = resolve({
            mode: 'tiled',
            pinnedAccountId: 'account-b',
        });

        expect(layout.arrangement).toBe('grid');
        expect(cellIds(layout)).toEqual(['account-b', 'account-a', SELF]);
    });
});

describe('resolveMeetingLayout — spotlight mode', () => {
    it('stages the pinned participant ahead of a screen share', () => {
        const layout = resolve({
            mode: 'spotlight',
            pinnedAccountId: 'account-b',
            hasScreenShare: true,
            activeSpeakerId: 'account-a',
        });

        expect(subjectId(layout)).toBe('account-b');
    });

    it('stages the screen share ahead of the active speaker', () => {
        const layout = resolve({
            mode: 'spotlight',
            hasScreenShare: true,
            activeSpeakerId: 'account-a',
        });

        expect(subjectId(layout)).toBe('screenShare');
    });

    it('stages the active speaker when nothing else claims the stage', () => {
        const layout = resolve({
            mode: 'spotlight',
            activeSpeakerId: 'account-a',
        });

        expect(subjectId(layout)).toBe('account-a');
    });

    it('falls back to the first participant when the room is silent', () => {
        const layout = resolve({ mode: 'spotlight' });

        expect(subjectId(layout)).toBe(SELF);
    });

    it('ignores an active speaker who has already left the room', () => {
        const layout = resolve({
            mode: 'spotlight',
            activeSpeakerId: 'account-gone',
        });

        expect(subjectId(layout)).toBe(SELF);
    });

    it('excludes the staged participant from the filmstrip', () => {
        const layout = resolve({
            mode: 'spotlight',
            activeSpeakerId: 'account-a',
        });

        expect(cellIds(layout)).toEqual(['account-b', SELF]);
    });

    it('keeps everyone in the filmstrip while a screen holds the stage', () => {
        const layout = resolve({ mode: 'spotlight', hasScreenShare: true });

        expect(cellIds(layout)).toEqual(['account-a', 'account-b', SELF]);
    });
});

describe('resolveMeetingLayout — overflow', () => {
    const many = participants(
        SELF,
        ...Array.from({ length: 12 }, (_, index) => `account-${index}`),
    );

    it('collapses the grid past its cap into a single "+N" cell', () => {
        const layout = resolve({ mode: 'tiled', participants: many });

        expect(layout.cells).toHaveLength(GRID_CELL_CAP);
        expect(layout.overflowCount).toBe(6);
        expect(cellIds(layout)).toContain('+6');
    });

    it('collapses the filmstrip past its own, lower cap', () => {
        const layout = resolve({
            mode: 'spotlight',
            participants: many,
            hasScreenShare: true,
        });

        expect(layout.cells).toHaveLength(FILMSTRIP_CELL_CAP);
        expect(layout.overflowCount).toBe(8);
    });

    it('never collapses away self or the pinned participant', () => {
        const layout = resolve({
            mode: 'tiled',
            participants: many,
            pinnedAccountId: 'account-11',
        });

        expect(cellIds(layout)[0]).toBe('account-11');
        expect(cellIds(layout).at(-1)).toBe(SELF);
    });

    it('reports no overflow when everyone fits', () => {
        const layout = resolve({ mode: 'tiled' });

        expect(layout.overflowCount).toBe(0);
        expect(cellIds(layout)).not.toContain('+0');
    });
});

describe('resolveMeetingLayout — edge cases', () => {
    it('returns an empty grid for an empty room', () => {
        const layout = resolve({ mode: 'auto', participants: [] });

        expect(layout.arrangement).toBe('grid');
        expect(layout.cells).toEqual([]);
    });

    it('degrades spotlight to the grid when there is nobody to stage', () => {
        const layout = resolve({ mode: 'spotlight', participants: [] });

        expect(layout.arrangement).toBe('grid');
    });

    it('stages the shared screen in an otherwise empty room', () => {
        const layout = resolve({
            mode: 'spotlight',
            participants: [],
            hasScreenShare: true,
        });

        expect(subjectId(layout)).toBe('screenShare');
        expect(layout.cells).toEqual([]);
    });

    it('leaves a lone participant an empty filmstrip in spotlight', () => {
        const layout = resolve({
            mode: 'spotlight',
            participants: participants(SELF),
        });

        expect(subjectId(layout)).toBe(SELF);
        expect(layout.cells).toEqual([]);
    });

    it('treats a pin on a departed participant as unpinned', () => {
        const layout = resolve({
            mode: 'auto',
            pinnedAccountId: 'account-gone',
        });

        expect(layout.arrangement).toBe('grid');
        expect(cellIds(layout)).toEqual(['account-a', 'account-b', SELF]);
    });
});

describe('reconcilePinnedAccountId', () => {
    it('keeps a pin whose participant is still in the room', () => {
        expect(
            reconcilePinnedAccountId(
                'account-a',
                participants(SELF, 'account-a'),
            ),
        ).toBe('account-a');
    });

    it('clears a pin whose participant has left', () => {
        expect(
            reconcilePinnedAccountId('account-a', participants(SELF)),
        ).toBeNull();
    });

    it('passes an absent pin straight through', () => {
        expect(reconcilePinnedAccountId(null, participants(SELF))).toBeNull();
    });
});
