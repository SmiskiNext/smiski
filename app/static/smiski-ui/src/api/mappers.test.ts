import { describe, expect, it } from 'vitest';
import {
    meetingFromBackend,
    meetingsFromBackend,
    participantsFromBackend,
    permissionsFromBackend,
} from './mappers';

describe('backend API mappers', () => {
    it('maps an OpenAPI meeting response envelope to the frontend domain model', () => {
        const meeting = meetingFromBackend({
            meeting: {
                id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
                hostId: 'account-123',
                status: 'RUNNING',
                title: 'Daily standup',
                description: 'Quick sync',
                issueLink: {
                    issueId: '10001',
                    issueKey: 'PROJ-1',
                    projectKey: 'PROJ',
                },
                organizerDisplayName: 'Host User',
                createdAt: '2026-01-01T00:00:00Z',
            },
        });

        expect(meeting).toMatchObject({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
            issueKey: 'PROJ-1',
            projectKey: 'PROJ',
            hostId: 'account-123',
            hostName: 'Host User',
            status: 'RUNNING',
            startedAt: '2026-01-01T00:00:00Z',
        });
    });

    it("keeps a SCHEDULED-type meeting's scheduledAt/startedAt once it has COMPLETED", () => {
        const meeting = meetingFromBackend({
            meeting: {
                id: 'm-1',
                type: 'SCHEDULED',
                status: 'COMPLETED',
                title: 'Sprint planning',
                startTime: '2026-01-01T09:00:00Z',
                createdAt: '2025-12-20T00:00:00Z',
                endedAt: '2026-01-01T10:00:00Z',
            },
        });

        expect(meeting).toMatchObject({
            status: 'COMPLETED',
            scheduledAt: '2026-01-01T09:00:00Z',
            startedAt: '2026-01-01T09:00:00Z',
            endedAt: '2026-01-01T10:00:00Z',
        });
    });

    it('drops scheduledAt/startedAt for a CANCELED meeting that never ran', () => {
        const meeting = meetingFromBackend({
            meeting: {
                id: 'm-2',
                type: 'SCHEDULED',
                status: 'CANCELED',
                title: 'Retro',
                startTime: '2026-01-01T09:00:00Z',
                createdAt: '2025-12-20T00:00:00Z',
            },
        });

        expect(meeting.scheduledAt).toBe('2026-01-01T09:00:00Z');
        expect(meeting.startedAt).toBeUndefined();
    });

    it('maps a list-page envelope to domain meetings', () => {
        const meetings = meetingsFromBackend({
            data: [
                {
                    id: 'm-3',
                    type: 'INSTANT',
                    status: 'RUNNING',
                    title: 'Huddle',
                    issueKey: 'PROJ-9',
                    createdAt: '2026-01-01T00:00:00Z',
                },
            ],
            meta: { size: 1, hasNext: false },
        });

        expect(meetings).toEqual([
            expect.objectContaining({
                id: 'm-3',
                issueKey: 'PROJ-9',
                projectKey: 'PROJ',
                status: 'RUNNING',
                startedAt: '2026-01-01T00:00:00Z',
            }),
        ]);
    });

    it('maps a participant list envelope to the frontend domain model', () => {
        const participants = participantsFromBackend({
            participants: [
                {
                    accountId: 'account-123',
                    displayName: 'Host User',
                    role: 'HOST',
                    joinedAt: '2026-01-01T00:00:00Z',
                },
            ],
        });

        expect(participants).toEqual([
            {
                accountId: 'account-123',
                displayName: 'Host User',
                role: 'HOST',
                joinedAt: '2026-01-01T00:00:00Z',
                leftAt: undefined,
            },
        ]);
    });

    it('uses the participantCount override instead of the always-1 backend default', () => {
        const meeting = meetingFromBackend(
            {
                meeting: {
                    id: 'm-4',
                    status: 'RUNNING',
                    title: 'Standup',
                },
            },
            { participantCount: 3 },
        );

        expect(meeting.participantCount).toBe(3);
    });

    it('maps permission envelopes flexibly', () => {
        expect(
            permissionsFromBackend({
                canViewMeeting: true,
                hasEditMeeting: false,
            }),
        ).toEqual({
            hasViewMeeting: true,
            hasEditMeeting: false,
        });
    });
});
