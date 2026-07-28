import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: (...args: unknown[]) => invokeMock(...args),
}));

import { createInstantMeeting } from './meetings';

describe('createInstantMeeting (real backend via invoke)', () => {
    beforeEach(() => {
        invokeMock.mockReset();
    });

    it('invokes the resolver and maps the {meeting, livekit} response to a Meeting', async () => {
        invokeMock.mockResolvedValue({
            meeting: {
                id: 'm-instant-1',
                hostId: 'acc-host',
                status: 'RUNNING',
                title: 'Incident sync',
                issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
                organizerDisplayName: 'Host User',
                createdAt: '2026-01-01T00:00:00Z',
            },
            livekit: { token: 'tok', roomName: 'room-1' },
        });

        const meeting = await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Incident sync',
            invitees: [
                {
                    accountId: 'acc-alice',
                    displayName: 'Alice',
                    email: 'alice@example.com',
                },
            ],
            host: {
                accountId: 'acc-host',
                displayName: 'Host User',
                email: 'host@example.com',
            },
        });

        expect(invokeMock).toHaveBeenCalledWith(
            'createInstantMeeting',
            expect.objectContaining({
                issueKey: 'SMISKI-101',
                title: 'Incident sync',
                invitees: [
                    {
                        accountId: 'acc-alice',
                        displayName: 'Alice',
                        email: 'alice@example.com',
                    },
                ],
            }),
        );
        expect(meeting).toMatchObject({
            id: 'm-instant-1',
            issueKey: 'SMISKI-101',
            status: 'RUNNING',
        });
    });

    it('surfaces a backend failure to the caller without any mock fallback', async () => {
        invokeMock.mockRejectedValue(new Error('VALIDATION_ERROR: title'));

        await expect(
            createInstantMeeting({
                issueKey: 'SMISKI-101',
                title: '',
                host: { accountId: 'acc-host', displayName: 'Host User' },
            }),
        ).rejects.toThrow('VALIDATION_ERROR: title');
        expect(invokeMock).toHaveBeenCalledTimes(1);
    });
});
