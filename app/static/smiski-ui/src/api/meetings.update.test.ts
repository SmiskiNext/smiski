import { describe, expect, it, vi } from 'vitest';

// `meetings.ts` imports `@forge/bridge`, which connects to the Custom UI bridge
// at module load and throws outside Jira. Stub it so the pure payload builder
// can be imported and tested in the node vitest environment.
vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), invokeRemote: vi.fn() }));

import type { Meeting } from '../domain';
import { buildUpdateMeetingPayload } from './meetings';

const DETAIL: Meeting = {
    id: 'm-1',
    title: 'Old title',
    projectId: 'project-smiski',
    projectKey: 'SMISKI',
    issueId: 'issue-SMISKI-101',
    issueKey: 'SMISKI-101',
    creatorId: 'acc-host',
    creatorName: 'Host User',
    hostId: 'acc-host',
    hostName: 'Host User',
    scheduledAt: '2026-08-01T02:00:00.000Z',
    status: 'SCHEDULED',
    participantCount: 1,
    endTime: '2026-08-01T03:00:00.000Z',
    zoneId: 'Asia/Ho_Chi_Minh',
    settings: {
        admissionPolicy: 'MANUAL_APPROVAL',
        maxParticipants: 25,
        allowScreenShare: false,
        chatEnabled: true,
        allowMicrophone: true,
        allowVideo: true,
    },
};

describe('buildUpdateMeetingPayload (MeetUpdateMeetingRequest body)', () => {
    it('carries issueLink and zoneId forward from detail unchanged', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: 'New description',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: DETAIL,
        });

        expect(payload.title).toBe('New title');
        expect(payload.description).toBe('New description');
        expect(payload.issueLink).toEqual({
            issueId: 'issue-SMISKI-101',
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });
        expect(payload.zoneId).toBe('Asia/Ho_Chi_Minh');
    });

    it("builds timeRange from the new start time and the detail's existing end time", () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: '',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: DETAIL,
        });

        expect(payload.timeRange).toEqual({
            startTime: '2026-08-01T02:30:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
        });
    });

    it('omits timeRange when the detail has no end time', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: '',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: { ...DETAIL, endTime: undefined },
        });

        expect(payload.timeRange).toBeUndefined();
    });

    it('falls back to the local zone when detail lacks one', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: '',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: { ...DETAIL, settings: undefined, zoneId: undefined },
        });

        expect(payload.zoneId).toBeTruthy();
    });
});
