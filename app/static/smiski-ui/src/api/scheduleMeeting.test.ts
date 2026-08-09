import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

import { scheduleMeeting } from './meetings';

function invokeResult(status: number, body: unknown) {
    return {
        status,
        headers: { 'content-type': 'application/json' },
        body,
    };
}

const baseInput = {
    issueId: '10001',
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

describe('scheduleMeeting (SDK schedule over Forge Remote)', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
    });

    it('issues the SDK schedule call over the adapter and maps the SCHEDULED response', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(201, {
                meeting: {
                    id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d92',
                    hostId: 'acc-host',
                    status: 'SCHEDULED',
                    title: 'Sprint planning',
                    issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
                    startTime: '2026-08-01T02:00:00.000Z',
                    endTime: '2026-08-01T03:00:00.000Z',
                    createdAt: '2026-07-01T00:00:00.000Z',
                },
            }),
        );

        const result = await scheduleMeeting(baseInput);

        expect(invokeRemoteMock).toHaveBeenCalledWith(
            expect.objectContaining({
                path: '/api/1/meetings:schedule',
                method: 'POST',
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
        invokeRemoteMock.mockResolvedValue(
            invokeResult(201, {
                meeting: {
                    id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d93',
                    status: 'SCHEDULED',
                    issueLink: { issueKey: 'SMISKI-101' },
                    createdAt: '2026-07-01T00:00:00.000Z',
                },
            }),
        );

        await scheduleMeeting(baseInput);

        const [options] = invokeRemoteMock.mock.calls[0];
        const body = options.body;
        expect(body).toMatchObject({
            title: 'Sprint planning',
            issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            settings: { admissionPolicy: 'ALLOW_ALL' },
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
        expect(body).not.toHaveProperty('host');

        const headerNames = Object.keys(options.headers ?? {}).map((name) =>
            name.toLowerCase(),
        );
        expect(headerNames).not.toContain('x-tenant-id');
        expect(headerNames).not.toContain('x-account-id');
    });

    it('surfaces a backend Problem Details rejection as result.error with no mock fallback', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(400, {
                code: 'MEETING_START_IN_PAST',
                title: 'Validation',
                detail: 'startTime must not be in the past',
            }),
        );

        const result = await scheduleMeeting(baseInput);

        expect(result.data).toBeUndefined();
        expect(result.error).toMatchObject({
            message: 'startTime must not be in the past',
            code: 'MEETING_START_IN_PAST',
        });
        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
    });

    it('surfaces an unreachable backend as result.error with no mock fallback', async () => {
        invokeRemoteMock.mockRejectedValue(new Error('remote unreachable'));

        const result = await scheduleMeeting(baseInput);

        expect(result.data).toBeUndefined();
        expect(result.error?.message).toBe('remote unreachable');
        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
    });
});
