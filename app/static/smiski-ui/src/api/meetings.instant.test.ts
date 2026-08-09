import { describe, expect, it, vi } from 'vitest';

// `meetings.ts` imports `@forge/bridge`, which connects to the Custom UI bridge
// at module load and throws outside Jira. Stub it so the pure payload builder
// can be imported and tested in the node vitest environment.
vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), invokeRemote: vi.fn() }));

import { buildInstantMeetingPayload } from './meetings';

describe('buildInstantMeetingPayload (MeetCreateInstantMeetingRequest body)', () => {
    it('sends each selected invitee with email, accountId, and displayName', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueId: '10001',
                issueKey: 'SMISKI-101',
                title: 'Incident sync',
                description: 'Coordinate the incident response',
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
                host: {
                    accountId: 'acc-host',
                    displayName: 'Host User',
                    email: 'host@example.com',
                },
            },
            'web-device-123',
        );

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
        expect(payload.host.deviceId).toBe('web-device-123');
        expect(payload.organizerEmail).toBe('host@example.com');
        expect(payload.organizerDisplayName).toBe('Host User');
        expect(payload.issueLink).toEqual({
            issueId: '10001',
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });
        expect(payload.settings.admissionPolicy).toBe('ALLOW_ALL');
        expect(payload.zoneId).toBeTruthy();
    });

    it('carries the profile-resolved zoneId through to the payload', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueId: '10009',
                issueKey: 'SMISKI-9',
                title: 'Zoned meeting',
                description: 'Discuss the regional rollout',
                zoneId: 'Asia/Ho_Chi_Minh',
            },
            'web-device-123',
        );
        expect(payload.zoneId).toBe('Asia/Ho_Chi_Minh');
    });

    it('sends an empty invitee list when no invitees are selected', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueId: '10009',
                issueKey: 'SMISKI-9',
                title: 'Solo meeting',
                description: 'Review the open work',
            },
            'web-device-123',
        );
        expect(payload.invitees).toEqual([]);
    });

    it('trims the required description without replacing it with the title', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueId: '10009',
                issueKey: 'SMISKI-9',
                title: 'Solo meeting',
                description: '  Focused review  ',
            },
            'web-device-123',
        );
        expect(payload.description).toBe('Focused review');
    });

    it('defaults settings when the caller supplies none', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueId: '10009',
                issueKey: 'SMISKI-9',
                title: 'Solo meeting',
                description: 'Review the open work',
            },
            'web-device-123',
        );
        expect(payload.settings).toEqual({
            admissionPolicy: 'ALLOW_ALL',
            maxParticipants: 50,
            allowScreenShare: true,
            chatEnabled: true,
            allowMicrophone: true,
            allowVideo: true,
        });
    });

    it("merges all 'Advanced settings' form values over the defaults", () => {
        const payload = buildInstantMeetingPayload(
            {
                issueId: '10009',
                issueKey: 'SMISKI-9',
                title: 'Locked-down meeting',
                description: 'Review sensitive work',
                settings: {
                    admissionPolicy: 'MANUAL_APPROVAL',
                    maxParticipants: 10,
                    allowScreenShare: false,
                    chatEnabled: false,
                    allowMicrophone: false,
                    allowVideo: false,
                },
            },
            'web-device-123',
        );
        expect(payload.settings).toEqual({
            admissionPolicy: 'MANUAL_APPROVAL',
            maxParticipants: 10,
            allowScreenShare: false,
            chatEnabled: false,
            allowMicrophone: false,
            allowVideo: false,
        });
    });
});
