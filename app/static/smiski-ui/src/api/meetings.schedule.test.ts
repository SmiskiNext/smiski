import { describe, expect, it, vi } from 'vitest';

// `meetings.ts` imports `@forge/bridge`, which connects to the Custom UI bridge
// at module load and throws outside Jira. Stub it so the pure payload builder
// can be imported and tested in the node vitest environment.
vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), requestRemote: vi.fn() }));

import { buildScheduleMeetingPayload } from './meetings';

describe('buildScheduleMeetingPayload', () => {
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

    it('carries the time range and profile zone, and never a host object', () => {
        const payload = buildScheduleMeetingPayload({
            issueKey: 'SMISKI-101',
            title: 'Sprint planning',
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
            zoneId: 'Asia/Ho_Chi_Minh',
            invitees: [],
        });

        expect(payload.timeRange).toEqual({
            startTime: '2026-08-01T02:00:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
        });
        expect(payload.zoneId).toBe('Asia/Ho_Chi_Minh');
        expect(payload.issueLink).toMatchObject({
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
});
