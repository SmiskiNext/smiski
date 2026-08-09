import { describe, expect, it } from 'vitest';
import type { Meeting, MeetingStatus } from './index';
import {
    canDeleteMeeting,
    getAvailableMeetingActions,
    getDeletableMeetingIds,
    pruneMeetingSelection,
    resolveMeetingPermissions,
} from './meetingPolicy';

function meeting(status: MeetingStatus): Meeting {
    return {
        id: 'meeting-1',
        title: 'Policy test',
        projectId: 'project-1',
        projectKey: 'TEST',
        issueId: 'issue-1',
        issueKey: 'TEST-1',
        creatorId: 'user-1',
        creatorName: 'Creator',
        hostId: 'user-1',
        hostName: 'Host',
        status,
        participantCount: 0,
    };
}

describe('resolveMeetingPermissions', () => {
    it('treats Edit Meeting as including View Meeting', () => {
        expect(resolveMeetingPermissions(false, true)).toMatchObject({
            canViewMeeting: true,
            canEditMeeting: true,
        });
    });
});

describe('getAvailableMeetingActions', () => {
    const noAccess = resolveMeetingPermissions(false, false);
    const viewOnly = resolveMeetingPermissions(true, false);
    const edit = resolveMeetingPermissions(false, true);

    const HOST = 'user-1';
    const NON_HOST = 'user-2';

    it('returns no actions while permissions are loading', () => {
        expect(
            getAvailableMeetingActions(
                meeting('RUNNING'),
                { ...edit, isLoading: true },
                HOST,
            ),
        ).toEqual([]);
    });

    it('returns no actions without View or Edit Meeting', () => {
        expect(
            getAvailableMeetingActions(meeting('SCHEDULED'), noAccess, HOST),
        ).toEqual([]);
    });

    it('gates scheduled meeting actions', () => {
        expect(
            getAvailableMeetingActions(meeting('SCHEDULED'), viewOnly, HOST),
        ).toEqual(['JOIN', 'VIEW_DETAIL']);
        expect(
            getAvailableMeetingActions(meeting('SCHEDULED'), edit, HOST),
        ).toEqual(['VIEW_DETAIL', 'EDIT', 'START', 'CANCEL', 'DELETE']);
    });

    it('gates running meeting actions', () => {
        expect(
            getAvailableMeetingActions(meeting('RUNNING'), viewOnly, HOST),
        ).toEqual(['JOIN', 'VIEW_DETAIL']);
        expect(
            getAvailableMeetingActions(meeting('RUNNING'), edit, HOST),
        ).toEqual(['JOIN', 'VIEW_DETAIL', 'EDIT', 'END']);
    });

    it.each(['COMPLETED', 'CANCELED'] as const)(
        'exposes EDIT to host with Edit Meeting on %s meetings',
        (status) => {
            expect(
                getAvailableMeetingActions(meeting(status), viewOnly, HOST),
            ).toEqual(['VIEW_DETAIL', 'VIEW_HISTORY']);
            expect(
                getAvailableMeetingActions(meeting(status), edit, HOST),
            ).toEqual(['VIEW_DETAIL', 'VIEW_HISTORY', 'EDIT', 'DELETE']);
        },
    );

    // The backend enforces EDIT/CANCEL/END as host-only
    // (`hostId.equals(actor)` in CancelMeetingApplicationService /
    // EndMeetingApplicationService / UpdateMeetingApplicationService), not
    // "any Edit Meeting permission holder" — a non-host Edit-Meeting user
    // must not see host actions. START is also host-only in the UI so manual
    // admission cannot turn a purported start into a pending join request.
    it('hides host actions from a non-host Edit Meeting user, keeps JOIN', () => {
        expect(
            getAvailableMeetingActions(meeting('SCHEDULED'), edit, NON_HOST),
        ).toEqual(['JOIN', 'VIEW_DETAIL']);
    });

    it('hides EDIT/END from a non-host Edit Meeting user, keeps JOIN', () => {
        expect(
            getAvailableMeetingActions(meeting('RUNNING'), edit, NON_HOST),
        ).toEqual(['JOIN', 'VIEW_DETAIL']);
    });

    it('hides EDIT from a non-host Edit Meeting user on terminal meetings', () => {
        expect(
            getAvailableMeetingActions(meeting('COMPLETED'), edit, NON_HOST),
        ).toEqual(['VIEW_DETAIL', 'VIEW_HISTORY']);
        expect(
            getAvailableMeetingActions(meeting('CANCELED'), edit, NON_HOST),
        ).toEqual(['VIEW_DETAIL', 'VIEW_HISTORY']);
    });

    it('offers DELETE only to the host with edit permission on non-running meetings', () => {
        expect(canDeleteMeeting(meeting('SCHEDULED'), edit, HOST)).toBe(true);
        expect(canDeleteMeeting(meeting('COMPLETED'), edit, HOST)).toBe(true);
        expect(canDeleteMeeting(meeting('CANCELED'), edit, HOST)).toBe(true);
        expect(canDeleteMeeting(meeting('RUNNING'), edit, HOST)).toBe(false);
        expect(canDeleteMeeting(meeting('SCHEDULED'), edit, NON_HOST)).toBe(
            false,
        );
        expect(canDeleteMeeting(meeting('SCHEDULED'), viewOnly, HOST)).toBe(
            false,
        );
    });

    it('selects only deletable meetings across mixed ownership and statuses', () => {
        const meetings = [
            meeting('SCHEDULED'),
            { ...meeting('RUNNING'), id: 'meeting-running' },
            {
                ...meeting('COMPLETED'),
                id: 'meeting-other-host',
                hostId: NON_HOST,
            },
            { ...meeting('CANCELED'), id: 'meeting-canceled' },
        ];

        expect([...getDeletableMeetingIds(meetings, edit, HOST)]).toEqual([
            'meeting-1',
            'meeting-canceled',
        ]);
    });

    it('prunes IDs that disappear or become ineligible after refetch', () => {
        expect([
            ...pruneMeetingSelection(
                new Set(['meeting-1', 'meeting-running', 'removed']),
                new Set(['meeting-1']),
            ),
        ]).toEqual(['meeting-1']);
    });
});
