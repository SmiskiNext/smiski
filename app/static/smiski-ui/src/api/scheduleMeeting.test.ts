import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import { scheduleMeeting } from './meetings';

/** Minimal WHATWG `Response` stand-in for the fields `requestRemote` callers read. */
function remoteResponse(
    ok: boolean,
    status: number,
    body: unknown,
): { ok: boolean; status: number; json: () => Promise<unknown> } {
    return { ok, status, json: async () => body };
}

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
};

describe('scheduleMeeting (real backend via Forge Remote)', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('calls requestRemote with the meet remote/path/body and maps the SCHEDULED response', async () => {
        requestRemoteMock.mockResolvedValue(
            remoteResponse(true, 201, {
                id: 'm-scheduled-1',
                hostId: 'acc-host',
                status: 'SCHEDULED',
                title: 'Sprint planning',
                issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
                startTime: '2026-08-01T02:00:00.000Z',
                endTime: '2026-08-01T03:00:00.000Z',
                createdAt: '2026-07-01T00:00:00Z',
            }),
        );

        const meeting = await scheduleMeeting(baseInput);

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: '/api/1/meetings:schedule',
                method: 'POST',
            }),
        );

        const [, options] = requestRemoteMock.mock.calls[0];
        const body = JSON.parse(options.body);
        expect(body).toMatchObject({
            title: 'Sprint planning',
            issueLink: { issueKey: 'SMISKI-101', projectKey: 'SMISKI' },
            timeRange: {
                startTime: '2026-08-01T02:00:00.000Z',
                endTime: '2026-08-01T03:00:00.000Z',
            },
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

        expect(meeting).toMatchObject({
            id: 'm-scheduled-1',
            issueKey: 'SMISKI-101',
            status: 'SCHEDULED',
        });
    });

    it('asserts no tenant/account identity header — Forge attaches only the FIT', async () => {
        requestRemoteMock.mockResolvedValue(
            remoteResponse(true, 201, {
                id: 'm-scheduled-2',
                status: 'SCHEDULED',
                issueLink: { issueKey: 'SMISKI-101' },
            }),
        );

        await scheduleMeeting(baseInput);

        const [, options] = requestRemoteMock.mock.calls[0];
        const headerNames = Object.keys(options.headers ?? {}).map((name) =>
            name.toLowerCase(),
        );
        expect(headerNames).not.toContain('x-tenant-id');
        expect(headerNames).not.toContain('x-account-id');
    });

    it('surfaces a backend problem+json failure to the caller without any mock fallback', async () => {
        requestRemoteMock.mockResolvedValue(
            remoteResponse(false, 400, {
                code: 'MEETING_START_IN_PAST',
                title: 'Validation',
                detail: 'startTime must not be in the past',
            }),
        );

        await expect(scheduleMeeting(baseInput)).rejects.toThrow(
            'startTime must not be in the past',
        );
        expect(requestRemoteMock).toHaveBeenCalledTimes(1);
    });
});
