import { describe, expect, it, vi } from 'vitest';

// `meetings.ts` imports `@forge/bridge`, which connects to the Custom UI bridge
// at module load and throws outside Jira. Stub it so the pure payload builder
// can be imported and tested in the node vitest environment.
vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), requestRemote: vi.fn() }));

import { buildInstantMeetingPayload } from './meetings';

describe('buildInstantMeetingPayload (MeetCreateInstantMeetingRequest body)', () => {
    it('sends each selected invitee with email, accountId, and displayName', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueKey: 'SMISKI-101',
                title: 'Incident sync',
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
            issueId: undefined,
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });
        expect(payload.settings.admissionPolicy).toBe('ALLOW_ALL');
        expect(payload.zoneId).toBeTruthy();
    });

    it('carries the profile-resolved zoneId through to the payload', () => {
        const payload = buildInstantMeetingPayload(
            {
                issueKey: 'SMISKI-9',
                title: 'Zoned meeting',
                zoneId: 'Asia/Ho_Chi_Minh',
            },
            'web-device-123',
        );
        expect(payload.zoneId).toBe('Asia/Ho_Chi_Minh');
    });

    it('sends an empty invitee list when no invitees are selected', () => {
        const payload = buildInstantMeetingPayload(
            { issueKey: 'SMISKI-9', title: 'Solo meeting' },
            'web-device-123',
        );
        expect(payload.invitees).toEqual([]);
    });

    it('defaults the description to the title when none is given', () => {
        const payload = buildInstantMeetingPayload(
            { issueKey: 'SMISKI-9', title: 'Solo meeting' },
            'web-device-123',
        );
        expect(payload.description).toBe('Solo meeting');
    });
});
