import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import {
    acceptPendingMeetingJoinRequests,
    declinePendingMeetingJoinRequests,
    listPendingMeetingJoinRequests,
    MeetingApiError,
} from './meetings';

function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
const REQUEST_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91';

describe('manual-admission meeting clients', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('lists and maps a page of pending join requests', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                results: [
                    {
                        requestId: REQUEST_ID,
                        accountId: 'account-42',
                        displayName: 'Alice Nguyen',
                        status: 'PENDING',
                        requestedAt: '2026-08-02T09:00:00Z',
                        expiresAt: '2026-08-02T09:05:00Z',
                    },
                ],
                meta: { total: 4, offset: 2, pageSize: 1 },
            }),
        );

        const result = await listPendingMeetingJoinRequests(MEETING_ID, {
            offset: 2,
            pageSize: 1,
        });

        expect(result).toEqual({
            requests: [
                {
                    requestId: REQUEST_ID,
                    accountId: 'account-42',
                    displayName: 'Alice Nguyen',
                    status: 'PENDING',
                    requestedAt: '2026-08-02T09:00:00Z',
                    expiresAt: '2026-08-02T09:05:00Z',
                },
            ],
            total: 4,
            offset: 2,
            pageSize: 1,
        });
        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: `/api/1/meetings/${MEETING_ID}/join-requests?offset=2&pageSize=1`,
                method: 'GET',
            }),
        );
    });

    it('accepts pending requests and preserves approved room credentials', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                results: [
                    {
                        requestId: REQUEST_ID,
                        status: 'APPROVED',
                        token: 'livekit-token',
                        roomName: 'meeting-room',
                        reason: null,
                    },
                ],
            }),
        );

        await expect(
            acceptPendingMeetingJoinRequests(MEETING_ID, [REQUEST_ID]),
        ).resolves.toEqual([
            {
                requestId: REQUEST_ID,
                status: 'APPROVED',
                token: 'livekit-token',
                roomName: 'meeting-room',
                reason: null,
            },
        ]);

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: `/api/1/meetings/${MEETING_ID}/join-requests:accept`,
                method: 'POST',
                body: JSON.stringify({ requestIds: [REQUEST_ID] }),
            }),
        );
    });

    it('declines pending requests and maps per-item failures', async () => {
        const missingRequestId = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d92';
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                results: [
                    {
                        requestId: REQUEST_ID,
                        status: 'DENIED',
                    },
                    {
                        requestId: missingRequestId,
                        status: 'FAILED',
                        reason: 'JOIN_REQUEST_NOT_FOUND',
                    },
                ],
            }),
        );

        await expect(
            declinePendingMeetingJoinRequests(MEETING_ID, [
                REQUEST_ID,
                missingRequestId,
            ]),
        ).resolves.toEqual([
            {
                requestId: REQUEST_ID,
                status: 'DENIED',
                token: null,
                roomName: null,
                reason: null,
            },
            {
                requestId: missingRequestId,
                status: 'FAILED',
                token: null,
                roomName: null,
                reason: 'JOIN_REQUEST_NOT_FOUND',
            },
        ]);
    });

    it('rejects an unknown decision status instead of guessing its meaning', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                results: [{ requestId: REQUEST_ID, status: 'PENDING' }],
            }),
        );

        await expect(
            acceptPendingMeetingJoinRequests(MEETING_ID, [REQUEST_ID]),
        ).rejects.toMatchObject({
            code: 'INVALID_JOIN_DECISION_RESPONSE',
        });
    });

    it('surfaces backend authorization failures as MeetingApiError', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(403, {
                code: 'NOT_AUTHORIZED',
                detail: 'Only the host may list pending join requests',
            }),
        );

        await expect(
            listPendingMeetingJoinRequests(MEETING_ID),
        ).rejects.toBeInstanceOf(MeetingApiError);
    });
});
