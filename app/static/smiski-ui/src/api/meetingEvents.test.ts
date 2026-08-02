// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./config', () => ({
    apiConfig: {
        apiBaseUrl: 'https://api.example',
        apiVersion: 1,
    },
}));

import {
    subscribeToMeetingJoinRequests,
    waitForJoinRequestDecision,
} from './meetingEvents';

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
const REQUEST_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91';

function eventResponse(event: string, data: unknown): Response {
    const encoder = new TextEncoder();
    return new Response(
        new ReadableStream({
            start(controller) {
                controller.enqueue(
                    encoder.encode(
                        `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`,
                    ),
                );
                controller.close();
            },
        }),
        { headers: { 'Content-Type': 'text/event-stream' } },
    );
}

describe('meeting event streams', () => {
    const fetchMock = vi.fn();

    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal('fetch', fetchMock);
    });

    it('maps host join-request events and stops after cancellation', async () => {
        const controller = new AbortController();
        const onJoinRequest = vi.fn(() => controller.abort());
        fetchMock.mockResolvedValue(
            eventResponse('join_request_created', {
                requestId: REQUEST_ID,
                accountId: 'account-42',
                displayName: 'Alice',
                requestedAt: '2026-08-02T10:00:00Z',
                expiresAt: '2026-08-02T10:10:00Z',
            }),
        );

        await subscribeToMeetingJoinRequests(MEETING_ID, {
            signal: controller.signal,
            onJoinRequest,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            `https://api.example/api/1/meetings/${MEETING_ID}/events`,
            expect.objectContaining({ method: 'GET' }),
        );
        expect(onJoinRequest).toHaveBeenCalledWith(
            expect.objectContaining({
                requestId: REQUEST_ID,
                status: 'PENDING',
                requestedAt: '2026-08-02T10:00:00Z',
                expiresAt: '2026-08-02T10:10:00Z',
            }),
        );
    });

    it('returns approved room credentials from the requester stream', async () => {
        fetchMock.mockResolvedValue(
            eventResponse('join_request_approved', {
                token: 'livekit-token',
                roomName: 'meeting-room',
            }),
        );

        await expect(
            waitForJoinRequestDecision(
                MEETING_ID,
                REQUEST_ID,
                new AbortController().signal,
            ),
        ).resolves.toEqual({
            status: 'APPROVED',
            token: 'livekit-token',
            roomName: 'meeting-room',
        });
    });

    it('returns a denial without inventing room credentials', async () => {
        fetchMock.mockResolvedValue(
            eventResponse('join_request_denied', {
                reason: 'HOST_DECLINED',
            }),
        );

        await expect(
            waitForJoinRequestDecision(
                MEETING_ID,
                REQUEST_ID,
                new AbortController().signal,
            ),
        ).resolves.toEqual({
            status: 'DENIED',
            reason: 'HOST_DECLINED',
        });
    });
});
