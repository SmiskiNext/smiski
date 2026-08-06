import { describe, expect, it, vi } from 'vitest';

// `meetings.ts` imports `@forge/bridge`, which connects to the Custom UI bridge
// at module load and throws outside Jira. Stub it so the pure payload builder
// can be imported and tested in the node vitest environment.
vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), invokeRemote: vi.fn() }));

import { buildScheduleMeetingPayload } from './meetings';

describe('buildScheduleMeetingPayload (MeetScheduleMeetingRequest body)', () => {
    it('sends each selected invitee with email, accountId, and displayName', () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-101',
            title: 'Sprint planning',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            zoneId: 'Asia/Ho_Chi_Minh',
            invitees: [
                {
                    accountId: 'acc-alice',
                    displayName: 'Alice Nguyen',
                    email: 'alice@example.com',
                },
                {
                    accountId: 'acc-bob',
                    displayName: 'Bob Tran',
                    email: 'bob@example.com',
                },
            ],
        });

        expect(payload.invitees).toEqual([
            {
                accountId: 'acc-alice',
                displayName: 'Alice Nguyen',
                email: 'alice@example.com',
            },
            {
                accountId: 'acc-bob',
                displayName: 'Bob Tran',
                email: 'bob@example.com',
            },
        ]);
    });

    it('carries the time range, organizer identity, and zone, and never a host object', () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-101',
            title: 'Sprint planning',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            zoneId: 'Asia/Ho_Chi_Minh',
            invitees: [],
            organizer: {
                accountId: 'acc-host',
                displayName: 'Host User',
                email: 'host@example.com',
            },
        });

        expect(payload.timeRange).toEqual({
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
        });
        expect(payload.zoneId).toBe('Asia/Ho_Chi_Minh');
        expect(payload.organizerEmail).toBe('host@example.com');
        expect(payload.organizerDisplayName).toBe('Host User');
        expect(payload.issueLink).toEqual({
            issueId: undefined,
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });
        expect(payload).not.toHaveProperty('host');
    });

    it('sends an empty invitee list when no invitees are selected', () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-9',
            title: 'Solo review',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            invitees: [],
        });
        expect(payload.invitees).toEqual([]);
    });

    it('defaults the description to the title when none is given', () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-9',
            title: 'Solo review',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            invitees: [],
        });
        expect(payload.description).toBe('Solo review');
    });

    it('defaults settings when the caller supplies none', () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-9',
            title: 'Solo review',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            invitees: [],
        });
        expect(payload.settings).toEqual({
            admissionPolicy: 'ALLOW_ALL',
            maxParticipants: 50,
            allowScreenShare: true,
            chatEnabled: true,
            allowMicrophone: true,
            allowVideo: true,
        });
    });

    it("merges the 'Advanced settings' form values over the defaults, keeping chatEnabled fixed", () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-9',
            title: 'Locked-down review',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            invitees: [],
            settings: {
                admissionPolicy: 'MANUAL_APPROVAL',
                maxParticipants: 10,
                allowScreenShare: false,
                allowMicrophone: false,
                allowVideo: false,
            },
        });
        expect(payload.settings).toEqual({
            admissionPolicy: 'MANUAL_APPROVAL',
            maxParticipants: 10,
            allowScreenShare: false,
            chatEnabled: true,
            allowMicrophone: false,
            allowVideo: false,
        });
    });
});
