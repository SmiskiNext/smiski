import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: (...args: unknown[]) => invokeMock(...args),
    requestRemote: vi.fn(),
}));

import { scheduleMeeting } from './meetings';

const baseInput = {
    issueKey: 'SMISKI-101',
    title: 'Sprint planning',
    startTime: '2026-08-01T02:00:00.000Z',
    endTime: '2026-08-01T03:00:00.000Z',
    zoneId: 'Asia/Ho_Chi_Minh',
    invitees: [
        {
            accountId: 'acc-alice',
            displayName: 'Alice',
            email: 'alice@example.com',
        },
    ],
    organizer: {
        accountId: 'acc-host',
        displayName: 'Host User',
        email: 'host@example.com',
    },
};

describe('scheduleMeeting (resolver scheduleMeeting, Forge KVS-backed)', () => {
    beforeEach(() => {
        invokeMock.mockReset();
    });

    it('invokes the scheduleMeeting resolver function and returns the SCHEDULED meeting', async () => {
        invokeMock.mockResolvedValue({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d92',
            hostId: 'acc-host',
            status: 'SCHEDULED',
            title: 'Sprint planning',
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });

        const result = await scheduleMeeting(baseInput);

        expect(invokeMock).toHaveBeenCalledWith(
            'scheduleMeeting',
            expect.objectContaining({
                title: 'Sprint planning',
                issueLink: { issueId: undefined, issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            }),
        );
        expect(result.data).toMatchObject({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d92',
            issueKey: 'SMISKI-101',
            status: 'SCHEDULED',
        });
        expect(result.error).toBeUndefined();
    });

    it('sends organizer identity, issueLink, settings, timeRange, zoneId and no host object', async () => {
        invokeMock.mockResolvedValue({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d93',
            status: 'SCHEDULED',
            issueKey: 'SMISKI-101',
        });

        await scheduleMeeting(baseInput);

        const [, payload] = invokeMock.mock.calls[0];
        expect(payload).toMatchObject({
            title: 'Sprint planning',
            issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            settings: { admissionPolicy: 'OPEN' },
            timeRange: {
                startTime: '2026-08-01T02:00:00.000Z',
                endTime: '2026-08-01T03:00:00.000Z',
            },
            organizerEmail: 'host@example.com',
            organizerDisplayName: 'Host User',
            zoneId: 'Asia/Ho_Chi_Minh',
            invitees: [
                {
                    accountId: 'acc-alice',
                    displayName: 'Alice',
                    email: 'alice@example.com',
                },
            ],
        });
        expect(payload).not.toHaveProperty('host');
    });

    it('surfaces a resolver rejection as result.error with no mock fallback', async () => {
        invokeMock.mockRejectedValue(
            new Error(
                'There was an error invoking the function - startTime must not be in the past.',
            ),
        );

        const result = await scheduleMeeting(baseInput);

        expect(result.data).toBeUndefined();
        expect(result.error?.message).toContain('startTime must not be in the past');
        expect(invokeMock).toHaveBeenCalledTimes(1);
    });
});
