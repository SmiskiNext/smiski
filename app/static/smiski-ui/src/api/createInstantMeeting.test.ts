import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import { createInstantMeeting } from './meetings';

const HOST = {
    accountId: 'acc-host',
    displayName: 'Host User',
    email: 'host@example.com',
};

function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

describe('createInstantMeeting (SDK createInstant over Forge Remote)', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('issues the SDK createInstant call over the adapter and returns the meeting on success', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(201, {
                meeting: {
                    id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
                    hostId: 'acc-host',
                    status: 'RUNNING',
                    title: 'Incident sync',
                    issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
                    organizerDisplayName: 'Host User',
                    createdAt: '2026-01-01T00:00:00.000Z',
                },
            }),
        );

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

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: '/api/1/meetings:instant',
                method: 'POST',
            }),
        );

        expect(result.data).toMatchObject({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
            issueKey: 'SMISKI-101',
            status: 'RUNNING',
        });
        expect(result.error).toBeUndefined();
    });

    it('sends a body with nested issueLink, settings, host, zoneId and no client identity headers', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(201, {
                meeting: {
                    id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91',
                    status: 'RUNNING',
                    issueLink: { issueKey: 'SMISKI-101' },
                    createdAt: '2026-01-01T00:00:00.000Z',
                },
            }),
        );

        await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Contract-shaped body',
            zoneId: 'Asia/Ho_Chi_Minh',
            host: HOST,
        });

        const [, options] = requestRemoteMock.mock.calls[0];
        const body = JSON.parse(options.body);
        expect(body).toMatchObject({
            title: 'Contract-shaped body',
            issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            settings: { admissionPolicy: 'ALLOW_ALL' },
            host: { displayName: 'Host User' },
            zoneId: 'Asia/Ho_Chi_Minh',
        });
        expect(body.host.deviceId).toBeTruthy();

        const headerNames = Object.keys(options.headers ?? {}).map((name) =>
            name.toLowerCase(),
        );
        expect(headerNames).not.toContain('x-tenant-id');
        expect(headerNames).not.toContain('x-account-id');
    });

    it('surfaces a backend problem+json rejection as result.error with no mock fallback', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(400, {
                code: 'VALIDATION_ERROR',
                title: 'Validation',
                detail: 'title must not be blank',
                traceId: 'trace-1',
            }),
        );

        const result = await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Rejected by backend',
            zoneId: 'Asia/Ho_Chi_Minh',
            host: HOST,
        });

        expect(result.data).toBeUndefined();
        expect(result.error).toMatchObject({
            message: 'title must not be blank',
            code: 'VALIDATION_ERROR',
            traceId: 'trace-1',
        });
        expect(requestRemoteMock).toHaveBeenCalledTimes(1);
    });

    it('surfaces an unreachable backend as result.error with no mock fallback', async () => {
        requestRemoteMock.mockRejectedValue(new Error('remote unreachable'));

        const result = await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'Unreachable backend',
            zoneId: 'Asia/Ho_Chi_Minh',
            host: HOST,
        });

        expect(result.data).toBeUndefined();
        expect(result.error?.message).toBe('remote unreachable');
        expect(requestRemoteMock).toHaveBeenCalledTimes(1);
    });
});
