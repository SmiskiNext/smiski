import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import { createInstantMeeting } from './meetings';

/** Minimal WHATWG `Response` stand-in for the fields `requestRemote` callers read. */
function remoteResponse(
    ok: boolean,
    status: number,
    body: unknown,
): { ok: boolean; status: number; json: () => Promise<unknown> } {
    return { ok, status, json: async () => body };
}

describe('createInstantMeeting (real backend via Forge Remote)', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('calls requestRemote with the meet remote/path/body and maps the {meeting, livekit} response', async () => {
        requestRemoteMock.mockResolvedValue(
            remoteResponse(true, 201, {
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
            }),
        );

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

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: '/api/1/meetings:instant',
                method: 'POST',
            }),
        );

        const [, options] = requestRemoteMock.mock.calls[0];
        expect(JSON.parse(options.body)).toMatchObject({
            issueKey: 'SMISKI-101',
            title: 'Incident sync',
            invitees: [
                {
                    accountId: 'acc-alice',
                    displayName: 'Alice',
                    email: 'alice@example.com',
                },
            ],
        });

        expect(meeting).toMatchObject({
            id: 'm-instant-1',
            issueKey: 'SMISKI-101',
            status: 'RUNNING',
        });
    });

    it('asserts no tenant/account identity header — Forge attaches only the FIT', async () => {
        requestRemoteMock.mockResolvedValue(
            remoteResponse(true, 201, {
                meeting: {
                    id: 'm-instant-2',
                    status: 'RUNNING',
                    issueLink: { issueKey: 'SMISKI-101' },
                },
            }),
        );

        await createInstantMeeting({
            issueKey: 'SMISKI-101',
            title: 'No identity headers',
            host: { accountId: 'acc-host', displayName: 'Host User' },
        });

        const [, options] = requestRemoteMock.mock.calls[0];
        const headerNames = Object.keys(options.headers ?? {}).map((name) =>
            name.toLowerCase(),
        );
        expect(headerNames).not.toContain('x-tenant-id');
        expect(headerNames).not.toContain('x-account-id');
    });

    it('surfaces a backend failure to the caller without any mock fallback', async () => {
        requestRemoteMock.mockResolvedValue(
            remoteResponse(false, 400, {
                code: 'VALIDATION_ERROR',
                title: 'Validation',
                detail: 'title must not be blank',
            }),
        );

        await expect(
            createInstantMeeting({
                issueKey: 'SMISKI-101',
                title: '',
                host: { accountId: 'acc-host', displayName: 'Host User' },
            }),
        ).rejects.toThrow('title must not be blank');
        expect(requestRemoteMock).toHaveBeenCalledTimes(1);
    });
});
