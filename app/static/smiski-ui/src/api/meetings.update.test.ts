import { describe, expect, it, vi } from 'vitest';

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
};

describe('buildUpdateMeetingPayload (MeetUpdateMeetingRequest body)', () => {
    it('maps every editable form field to the full-replace contract', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'Architecture review',
            description: 'Review the proposed service boundaries.',
            issueId: '10042',
            issueKey: 'SMISKI-42',
            projectKey: 'SMISKI',
            startTime: '2026-08-01T02:30:00.000Z',
            endTime: '2026-08-01T04:00:00.000Z',
            zoneId: 'Asia/Ho_Chi_Minh',
        });

        expect(payload).toEqual({
            title: 'Architecture review',
            description: 'Review the proposed service boundaries.',
            issueLink: {
                issueId: '10042',
                issueKey: 'SMISKI-42',
                projectKey: 'SMISKI',
            },
            zoneId: 'Asia/Ho_Chi_Minh',
            timeRange: {
                startTime: '2026-08-01T02:30:00.000Z',
                endTime: '2026-08-01T04:00:00.000Z',
            },
        });
    });

    it('always carries the numeric Jira issue id required by the backend', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'Architecture review',
            description: 'Review the proposed service boundaries.',
            issueId: '10043',
            issueKey: 'SMISKI-43',
            projectKey: 'SMISKI',
            startTime: '2026-08-01T02:30:00.000Z',
            endTime: '2026-08-01T04:00:00.000Z',
            zoneId: 'UTC',
        });

        expect(payload.issueLink).toEqual({
            issueId: '10043',
            issueKey: 'SMISKI-43',
            projectKey: 'SMISKI',
        });
    });

    it("carries detail fields and preserves the meeting's existing end time", () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: 'New description',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: DETAIL,
        });

        expect(payload.zoneId).toBe('Asia/Ho_Chi_Minh');
        expect(payload.timeRange).toEqual({
            startTime: '2026-08-01T02:30:00.000Z',
            endTime: '2026-08-01T03:00:00.000Z',
        });
    });

    it('omits timeRange when detail has no end time', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: '',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: { ...DETAIL, endTime: undefined },
        });

        expect(payload.timeRange).toBeUndefined();
    });

    it('uses the selected issue link when provided', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: '',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: DETAIL,
            selectedIssue: {
                issueId: 'issue-SMISKI-202',
                issueKey: 'SMISKI-202',
                projectKey: 'SMISKI',
            },
        });

        expect(payload.issueLink).toEqual({
            issueId: 'issue-SMISKI-202',
            issueKey: 'SMISKI-202',
            projectKey: 'SMISKI',
        });
    });

    it('preserves the current issue link when selectedIssue is omitted', () => {
        const payload = buildUpdateMeetingPayload({
            title: 'New title',
            description: '',
            startTime: '2026-08-01T02:30:00.000Z',
            detail: DETAIL,
        });

        expect(payload.issueLink).toEqual({
            issueId: 'issue-SMISKI-101',
            issueKey: 'SMISKI-101',
            projectKey: 'SMISKI',
        });
    });
});
