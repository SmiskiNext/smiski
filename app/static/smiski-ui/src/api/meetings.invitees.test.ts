import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import {
    addMeetingInvitees,
    getMeeting,
    MeetingApiError,
    removeMeetingInvitees,
} from './meetings';

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
const INVITEE_ID = '0195e0c2-8f3a-7c21-b9d4-3a2b1c4d5e60';

function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

const INVITEE_RESPONSE = {
    id: INVITEE_ID,
    accountId: 'account-456',
    email: 'alice@example.com',
    displayName: 'Alice Nguyen',
    role: 'REQ_PARTICIPANT',
    status: 'NEEDS_ACTION',
    invitedAt: '2026-08-02T10:35:00Z',
    respondedAt: null,
};

describe('meeting invitee APIs over Forge Remote', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('adds invitees with the backend identity contract', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, { invitees: [INVITEE_RESPONSE] }),
        );

        const result = await addMeetingInvitees(MEETING_ID, [
            {
                accountId: 'account-456',
                email: 'alice@example.com',
                displayName: 'Alice Nguyen',
            },
        ]);

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: `/api/1/meetings/${MEETING_ID}/invitees`,
                method: 'POST',
            }),
        );
        const [, options] = requestRemoteMock.mock.calls[0];
        expect(JSON.parse(options.body)).toEqual({
            invitees: [
                {
                    accountId: 'account-456',
                    email: 'alice@example.com',
                    displayName: 'Alice Nguyen',
                },
            ],
        });
        expect(result).toEqual([
            {
                id: INVITEE_ID,
                accountId: 'account-456',
                email: 'alice@example.com',
                displayName: 'Alice Nguyen',
                status: 'NEEDS_ACTION',
                invitedAt: '2026-08-02T10:35:00Z',
                respondedAt: undefined,
            },
        ]);
    });

    it('returns invitees embedded in the shared meeting-detail response', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                meeting: {
                    id: MEETING_ID,
                    hostId: 'account-host',
                    title: 'Sprint planning',
                    status: 'SCHEDULED',
                    issueLink: {
                        issueId: '10001',
                        issueKey: 'SMISKI-101',
                        projectKey: 'SMISKI',
                    },
                },
                invitees: [INVITEE_RESPONSE],
                participants: [],
            }),
        );

        const result = await getMeeting(MEETING_ID);

        expect(result.invitees).toEqual([
            expect.objectContaining({
                id: INVITEE_ID,
                accountId: 'account-456',
                status: 'NEEDS_ACTION',
            }),
        ]);
        expect(result.participants).toEqual([]);
    });

    it('removes invitees by invitee id through the batch endpoint', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, { invitees: [INVITEE_RESPONSE] }),
        );

        await removeMeetingInvitees(MEETING_ID, [INVITEE_ID]);

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: `/api/1/meetings/${MEETING_ID}/invitees:batchDelete`,
                method: 'POST',
            }),
        );
        const [, options] = requestRemoteMock.mock.calls[0];
        expect(JSON.parse(options.body)).toEqual({
            inviteeIds: [INVITEE_ID],
        });
    });

    it('surfaces atomic duplicate failures as MeetingApiError', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(409, {
                code: 'INVITEE_ALREADY_EXISTS',
                detail: 'Account account-456 is already an active invitee.',
            }),
        );

        await expect(
            addMeetingInvitees(MEETING_ID, [
                {
                    accountId: 'account-456',
                    email: 'alice@example.com',
                    displayName: 'Alice Nguyen',
                },
            ]),
        ).rejects.toMatchObject({
            name: 'MeetingApiError',
            code: 'INVITEE_ALREADY_EXISTS',
        });
    });

    it('surfaces host and status enforcement through the shared error type', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(403, {
                code: 'NOT_AUTHORIZED',
                detail: 'You are not the host of this meeting.',
            }),
        );

        await expect(
            removeMeetingInvitees(MEETING_ID, [INVITEE_ID]),
        ).rejects.toBeInstanceOf(MeetingApiError);
    });
});
