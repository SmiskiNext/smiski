import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import { batchDeleteMeetings, MeetingApiError } from './meetings';

function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

describe('batchDeleteMeetings (SDK batchDelete operation over Forge Remote)', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('sends meetingIds payload to POST /api/1/meetings:batchDelete', async () => {
        const id1 = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';
        const id2 = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d91';

        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                meetings: [
                    {
                        id: id1,
                        title: 'Meeting 1',
                        status: 'CANCELED',
                    },
                    {
                        id: id2,
                        title: 'Meeting 2',
                        status: 'CANCELED',
                    },
                ],
            }),
        );

        const deletedMeetings = await batchDeleteMeetings([id1, id2]);

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: '/api/1/meetings:batchDelete',
                method: 'POST',
            }),
        );

        const [, options] = requestRemoteMock.mock.calls[0];
        const body = JSON.parse(options.body);
        expect(body).toEqual({ meetingIds: [id1, id2] });

        expect(deletedMeetings).toHaveLength(2);
        expect(deletedMeetings[0].id).toBe(id1);
        expect(deletedMeetings[1].id).toBe(id2);
    });

    it('throws MeetingApiError on backend error response', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(403, {
                code: 'NOT_AUTHORIZED',
                title: 'Forbidden',
                detail: 'Only the host or project admin may delete meetings',
            }),
        );

        await expect(batchDeleteMeetings(['m1'])).rejects.toThrow(
            MeetingApiError,
        );
    });
});
