import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

import type { MeetingSettings } from '../domain';
import { MeetingApiError, updateMeetingSettings } from './meetings';

function invokeResult(status: number, body: unknown) {
    return {
        status,
        headers: { 'content-type': 'application/json' },
        body,
    };
}

const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';

const REQUEST: MeetingSettings = {
    admissionPolicy: 'MANUAL_APPROVAL',
    maxParticipants: 25,
    allowScreenShare: false,
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: false,
};

describe('updateMeetingSettings (SDK updateSettings over Forge Remote)', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
    });

    it('returns the settings block from the flat response, not a mismapped Meeting', async () => {
        // `MeetUpdateMeetingSettingsResponse` has no `id`/`meeting` wrapper and
        // no nested `settings` — this is the exact shape that used to be
        // routed through `meetingFromBackend` and silently produce a
        // near-empty `Meeting` (id: '', settings: undefined).
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                meetingId: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
                admissionPolicy: 'MANUAL_APPROVAL',
                maxParticipants: 25,
                allowScreenShare: false,
                chatEnabled: true,
                allowMicrophone: true,
                allowVideo: false,
            }),
        );

        const result = await updateMeetingSettings(MEETING_ID, REQUEST);

        expect(result).toEqual(REQUEST);
    });

    it('sends the full settings replacement body and no client identity headers', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                meetingId: MEETING_ID,
                ...REQUEST,
            }),
        );

        await updateMeetingSettings(MEETING_ID, REQUEST);

        expect(invokeRemoteMock).toHaveBeenCalledWith(
            expect.objectContaining({
                path: `/api/1/meetings/${MEETING_ID}/settings`,
                method: 'PUT',
            }),
        );

        const [options] = invokeRemoteMock.mock.calls[0];
        const body = options.body;
        expect(body).toEqual(REQUEST);

        const headerNames = Object.keys(options.headers ?? {}).map((name) =>
            name.toLowerCase(),
        );
        expect(headerNames).not.toContain('x-tenant-id');
        expect(headerNames).not.toContain('x-account-id');
    });

    it('throws a MeetingApiError on a backend rejection (e.g. non-host caller)', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(403, {
                code: 'NOT_AUTHORIZED',
                title: 'Forbidden',
                detail: 'Only the host may change the meeting settings',
                traceId: 'trace-1',
            }),
        );

        await expect(
            updateMeetingSettings(MEETING_ID, REQUEST),
        ).rejects.toMatchObject({
            message: 'Only the host may change the meeting settings',
            code: 'NOT_AUTHORIZED',
        });
    });

    it('is a MeetingApiError instance on rejection', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(409, {
                code: 'INVALID_STATUS_TRANSITION',
                title: 'Conflict',
                detail: 'Cannot change settings on a completed meeting',
            }),
        );

        await expect(
            updateMeetingSettings(MEETING_ID, REQUEST),
        ).rejects.toBeInstanceOf(MeetingApiError);
    });
});
