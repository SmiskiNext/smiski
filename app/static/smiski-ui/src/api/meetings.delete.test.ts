import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

import { deleteMeeting, MeetingApiError } from './meetings';

function invokeResult(status: number, body: unknown) {
    return {
        status,
        headers: { 'content-type': 'application/json' },
        body,
    };
}

describe('deleteMeeting', () => {
    const meetingId = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';

    beforeEach(() => invokeRemoteMock.mockReset());

    it('calls the generated DELETE operation and returns the deleted id', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                meeting: {
                    id: meetingId,
                    title: 'Retrospective',
                    status: 'DELETED',
                },
            }),
        );

        await expect(deleteMeeting(meetingId)).resolves.toBe(meetingId);
        expect(invokeRemoteMock).toHaveBeenCalledWith(
            expect.objectContaining({
                path: `/api/1/meetings/${meetingId}`,
                method: 'DELETE',
            }),
        );
    });

    it('surfaces backend policy errors', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(409, {
                code: 'MEETING_ALREADY_RUNNING',
                title: 'Conflict',
                detail: 'Running meetings must be ended before deletion.',
            }),
        );

        await expect(deleteMeeting(meetingId)).rejects.toThrow(MeetingApiError);
    });
});
