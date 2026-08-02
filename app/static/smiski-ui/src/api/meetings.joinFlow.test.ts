import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
const waitForDecisionMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));
vi.mock('./meetingEvents', () => ({
    waitForJoinRequestDecision: (...args: unknown[]) =>
        waitForDecisionMock(...args),
}));

import { joinMeeting, MeetingApiError } from './meetings';

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
const REQUEST_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91';
const IDENTITY = { displayName: 'Alice', deviceId: 'device-1' };

function joinResponse(body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
    });
}

describe('join meeting approval flow', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
        waitForDecisionMock.mockReset();
    });

    it('returns immediately when admission is approved', async () => {
        requestRemoteMock.mockResolvedValue(
            joinResponse({
                status: 'APPROVED',
                requestId: REQUEST_ID,
                token: 'token',
                roomName: 'room',
            }),
        );

        await expect(joinMeeting(MEETING_ID, IDENTITY)).resolves.toEqual({
            requestId: REQUEST_ID,
            token: 'token',
            roomName: 'room',
        });
        expect(waitForDecisionMock).not.toHaveBeenCalled();
    });

    it('waits for SSE approval when admission is pending', async () => {
        requestRemoteMock.mockResolvedValue(
            joinResponse({ status: 'PENDING', requestId: REQUEST_ID }),
        );
        waitForDecisionMock.mockResolvedValue({
            status: 'APPROVED',
            token: 'approved-token',
            roomName: 'room',
        });
        const onPending = vi.fn();
        const controller = new AbortController();

        await expect(
            joinMeeting(MEETING_ID, IDENTITY, {
                signal: controller.signal,
                onPending,
            }),
        ).resolves.toEqual({
            requestId: REQUEST_ID,
            token: 'approved-token',
            roomName: 'room',
        });
        expect(onPending).toHaveBeenCalledWith(REQUEST_ID);
        expect(waitForDecisionMock).toHaveBeenCalledWith(
            MEETING_ID,
            REQUEST_ID,
            controller.signal,
        );
    });

    it('surfaces an SSE denial as a domain-specific API error', async () => {
        requestRemoteMock.mockResolvedValue(
            joinResponse({ status: 'PENDING', requestId: REQUEST_ID }),
        );
        waitForDecisionMock.mockResolvedValue({
            status: 'DENIED',
            reason: 'HOST_DECLINED',
        });

        await expect(joinMeeting(MEETING_ID, IDENTITY)).rejects.toMatchObject({
            name: MeetingApiError.name,
            code: 'JOIN_REQUEST_DENIED',
            message: 'HOST_DECLINED',
        });
    });
});
