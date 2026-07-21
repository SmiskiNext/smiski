import { describe, expect, it } from 'vitest';
import type { Meeting, MeetingStatus } from '../../domain';
import { filterAndSortIssueMeetings } from './issueMeetingsFilter';

function meeting(
  id: string,
  status: MeetingStatus,
  title: string,
  timing: Partial<Pick<Meeting, 'scheduledAt' | 'startedAt' | 'endedAt'>> = {},
): Meeting {
  return {
    id,
    title,
    projectId: 'project-1',
    projectKey: 'TEST',
    issueId: 'issue-1',
    issueKey: 'TEST-1',
    creatorId: 'user-1',
    creatorName: 'Creator',
    hostId: 'user-1',
    hostName: 'Host',
    status,
    participantCount: 0,
    ...timing,
  };
}

describe('filterAndSortIssueMeetings', () => {
  it('filters by status', () => {
    const meetings = [meeting('1', 'RUNNING', 'A'), meeting('2', 'SCHEDULED', 'B')];
    expect(filterAndSortIssueMeetings(meetings, { status: 'SCHEDULED' }).map((m) => m.id)).toEqual([
      '2',
    ]);
  });

  it('filters by title, case-insensitively', () => {
    const meetings = [meeting('1', 'RUNNING', 'Sprint sync'), meeting('2', 'RUNNING', 'Retro')];
    expect(filterAndSortIssueMeetings(meetings, { search: 'sprint' }).map((m) => m.id)).toEqual([
      '1',
    ]);
  });

  it('sorts RUNNING before SCHEDULED before COMPLETED/CANCELED', () => {
    const meetings = [
      meeting('completed', 'COMPLETED', 'C'),
      meeting('scheduled', 'SCHEDULED', 'B'),
      meeting('running', 'RUNNING', 'A'),
      meeting('canceled', 'CANCELED', 'D'),
    ];
    expect(filterAndSortIssueMeetings(meetings, {}).map((m) => m.id)).toEqual([
      'running',
      'scheduled',
      'completed',
      'canceled',
    ]);
  });

  it('orders SCHEDULED meetings soonest-first', () => {
    const meetings = [
      meeting('later', 'SCHEDULED', 'Later', { scheduledAt: '2026-01-02T00:00:00.000Z' }),
      meeting('sooner', 'SCHEDULED', 'Sooner', { scheduledAt: '2026-01-01T00:00:00.000Z' }),
    ];
    expect(filterAndSortIssueMeetings(meetings, {}).map((m) => m.id)).toEqual([
      'sooner',
      'later',
    ]);
  });

  it('orders COMPLETED meetings most-recent-first', () => {
    const meetings = [
      meeting('older', 'COMPLETED', 'Older', { endedAt: '2026-01-01T00:00:00.000Z' }),
      meeting('newer', 'COMPLETED', 'Newer', { endedAt: '2026-01-02T00:00:00.000Z' }),
    ];
    expect(filterAndSortIssueMeetings(meetings, {}).map((m) => m.id)).toEqual(['newer', 'older']);
  });
});
