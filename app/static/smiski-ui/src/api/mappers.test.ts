import { describe, expect, it } from 'vitest';
import {
  meetingFromBackend,
  meetingsFromBackend,
  permissionsFromBackend,
  roomTokenFromBackend,
  scheduleMeetingRequest,
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

  it('maps common list response envelopes', () => {
    expect(
      meetingsFromBackend({
        items: [{ id: 'm-1', title: 'One', status: 'SCHEDULED', issueKey: 'PROJ-1' }],
      }),
    ).toHaveLength(1);
  });

  it('builds the current scheduled-meeting backend request body', () => {
    const request = scheduleMeetingRequest(
      {
        issueKey: 'PROJ-1',
        title: 'Planning',
        startTime: '2026-01-01T00:00:00Z',
        participantAccountIds: ['account-456'],
      },
      {
        currentUser: {
          accountId: 'account-123',
          displayName: 'Alice Nguyen',
          email: 'alice@example.com',
        },
        projectMembers: [
          {
            accountId: 'account-456',
            displayName: 'Bob Tran',
            email: 'bob@example.com',
          },
        ],
      },
    );

    expect(request).toMatchObject({
      title: 'Planning',
      issueLink: { issueKey: 'PROJ-1', projectKey: 'PROJ' },
      organizerEmail: 'alice@example.com',
      organizerDisplayName: 'Alice Nguyen',
      invitees: [{ accountId: 'account-456', displayName: 'Bob Tran', email: 'bob@example.com' }],
    });
    expect(request.timeRange).toEqual({
      startTime: '2026-01-01T00:00:00.000Z',
      endTime: '2026-01-01T01:00:00.000Z',
    });
  });

  it('maps permission and LiveKit token envelopes flexibly', () => {
    expect(
      permissionsFromBackend({ canViewMeeting: true, hasEditMeeting: false }),
    ).toEqual({
      hasViewMeeting: true,
      hasEditMeeting: false,
    });
    expect(roomTokenFromBackend({ livekit: { token: 'token', url: 'wss://livekit.example' } }))
      .toEqual({
        token: 'token',
        url: 'wss://livekit.example',
      });
  });
});
