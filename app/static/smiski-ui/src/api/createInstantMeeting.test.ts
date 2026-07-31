import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: (...args: unknown[]) => invokeMock(...args),
    requestRemote: vi.fn(),
}));

import { createInstantMeeting } from './meetings';

const HOST = {
    accountId: 'acc-host',
    displayName: 'Host User',
    email: 'host@example.com',
};

describe('createInstantMeeting (resolver createInstantMeeting, Forge KVS-backed)', () => {
    beforeEach(() => {
        invokeMock.mockReset();
    });

    it('invokes the createInstantMeeting resolver function and returns the created meeting', async () => {
        invokeMock.mockResolvedValue({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
            hostId: 'acc-host',
            status: 'RUNNING',
            title: 'Incident sync',
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });

        const result = await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Incident sync',
            zoneId: 'Asia/Ho_Chi_Minh',
            invitees: [
                {
                    accountId: 'acc-alice',
                    displayName: 'Alice',
                    email: 'alice@example.com',
                },
            ],
            host: HOST,
        });

        expect(invokeMock).toHaveBeenCalledWith(
            'createInstantMeeting',
            expect.objectContaining({
                title: 'Incident sync',
                issueLink: { issueId: undefined, issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            }),
        );
        expect(result.data).toMatchObject({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
            issueKey: 'SMISKI-101',
            status: 'RUNNING',
        });
        expect(result.error).toBeUndefined();
    });

    it('sends a payload with nested issueLink, settings, host, and zoneId', async () => {
        invokeMock.mockResolvedValue({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91',
            status: 'RUNNING',
            issueKey: 'SMISKI-101',
        });

        await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Contract-shaped body',
            zoneId: 'Asia/Ho_Chi_Minh',
            host: HOST,
        });

        const [, payload] = invokeMock.mock.calls[0];
        expect(payload).toMatchObject({
            title: 'Contract-shaped body',
            issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            settings: { admissionPolicy: 'OPEN' },
            host: { displayName: 'Host User' },
            zoneId: 'Asia/Ho_Chi_Minh',
        });
        expect(payload.host.deviceId).toBeTruthy();
    });

    it('surfaces a resolver rejection as result.error with no mock fallback', async () => {
        invokeMock.mockRejectedValue(
            new Error(
                'There was an error invoking the function - Edit Meeting permission required for project SMISKI.',
            ),
        );

        const result = await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Rejected by resolver',
            zoneId: 'Asia/Ho_Chi_Minh',
            host: HOST,
        });

        expect(result.data).toBeUndefined();
        expect(result.error?.message).toContain('Edit Meeting permission required');
        expect(invokeMock).toHaveBeenCalledTimes(1);
    });
});
