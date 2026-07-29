import { describe, expect, it } from 'vitest';
import {
    meetingFromBackend,
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
