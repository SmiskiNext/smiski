// @vitest-environment jsdom

import {
    cleanup,
    fireEvent,
    render,
    screen,
    waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    accountId: 'account-host',
    add: vi.fn(),
    remove: vi.fn(),
    invitees: [
        {
            id: 'invitee-1',
            accountId: 'account-alice',
            email: 'alice@example.com',
            displayName: 'Alice Nguyen',
            status: 'NEEDS_ACTION' as const,
        },
        {
            id: 'invitee-2',
            accountId: 'account-bob',
            email: 'bob@example.com',
            displayName: 'Bob Smith',
            status: 'ACCEPTED' as const,
        },
    ],
}));

vi.mock('../../context/CurrentUserContext', () => ({
    useCurrentUser: () => ({ accountId: mocks.accountId }),
}));

vi.mock('../../hooks/useMeetingInvitees', () => ({
    useMeetingInvitees: () => ({
        invitees: mocks.invitees,
        loading: false,
        error: null,
    }),
    useAddMeetingInvitees: () => ({
        mutateAsync: mocks.add,
        isPending: false,
    }),
    useRemoveMeetingInvitees: () => ({
        mutateAsync: mocks.remove,
        isPending: false,
    }),
}));

vi.mock('./WorkspaceUserPicker', () => ({
    WorkspaceUserPicker: ({
        onChange,
    }: {
        onChange: (
            users: Array<{
                accountId: string;
                displayName: string;
                email: string;
            }>,
        ) => void;
    }) => (
        <button
            type='button'
            onClick={() =>
                onChange([
                    {
                        accountId: 'account-charlie',
                        displayName: 'Charlie Tran',
                        email: 'charlie@example.com',
                    },
                ])
            }
        >
            Pick Charlie
        </button>
    ),
}));

import type { Meeting } from '../../domain';
import { MeetingInviteeManager } from './MeetingInviteeManager';

const MEETING: Meeting = {
    id: 'meeting-1',
    title: 'Sprint planning',
    projectId: 'project-smiski',
    projectKey: 'SMISKI',
    issueId: 'issue-SMISKI-101',
    issueKey: 'SMISKI-101',
    creatorId: 'account-host',
    creatorName: 'Host User',
    hostId: 'account-host',
    hostName: 'Host User',
    status: 'SCHEDULED',
    participantCount: 0,
};

describe('MeetingInviteeManager', () => {
    afterEach(cleanup);

    beforeEach(() => {
        mocks.accountId = 'account-host';
        mocks.add.mockReset().mockResolvedValue([]);
        mocks.remove.mockReset().mockResolvedValue([]);
    });

    it('is only rendered for scheduled meetings', () => {
        render(
            <MeetingInviteeManager
                meeting={{ ...MEETING, status: 'RUNNING' }}
            />,
        );

        expect(
            screen.queryByRole('region', { name: 'Meeting invitees' }),
        ).toBeNull();
    });

    it('adds selected Jira users with their complete backend identity', async () => {
        render(<MeetingInviteeManager meeting={MEETING} />);

        fireEvent.click(screen.getByRole('button', { name: 'Pick Charlie' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add invitees' }));

        await waitFor(() =>
            expect(mocks.add).toHaveBeenCalledWith({
                meetingId: MEETING.id,
                invitees: [
                    {
                        accountId: 'account-charlie',
                        displayName: 'Charlie Tran',
                        email: 'charlie@example.com',
                    },
                ],
            }),
        );
    });

    it('removes multiple selected invitees by invitee id', async () => {
        render(<MeetingInviteeManager meeting={MEETING} />);

        fireEvent.click(
            screen.getByRole('checkbox', { name: 'Select Alice Nguyen' }),
        );
        fireEvent.click(
            screen.getByRole('checkbox', { name: 'Select Bob Smith' }),
        );
        fireEvent.click(
            screen.getByRole('button', { name: 'Remove selected (2)' }),
        );

        await waitFor(() =>
            expect(mocks.remove).toHaveBeenCalledWith({
                meetingId: MEETING.id,
                inviteeIds: ['invitee-1', 'invitee-2'],
            }),
        );
    });

    it('shows RSVP state without management controls to a non-host', () => {
        mocks.accountId = 'account-viewer';

        render(<MeetingInviteeManager meeting={MEETING} />);

        expect(screen.getByText('Alice Nguyen')).toBeTruthy();
        expect(screen.getByText('Accepted')).toBeTruthy();
        expect(
            screen.queryByRole('button', { name: 'Pick Charlie' }),
        ).toBeNull();
        expect(screen.queryAllByRole('checkbox')).toHaveLength(0);
    });
});
