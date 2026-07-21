import { describe, expect, it } from 'vitest';
import type { Meeting, MeetingStatus } from './index';
import { getAvailableMeetingActions, resolveMeetingPermissions } from './meetingPolicy';

function meeting(status: MeetingStatus): Meeting {
  return {
    id: 'meeting-1',
    title: 'Policy test',
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
  };
}

describe('resolveMeetingPermissions', () => {
  it('treats Edit Meeting as including View Meeting', () => {
    expect(resolveMeetingPermissions(false, true)).toMatchObject({
      canViewMeeting: true,
      canEditMeeting: true,
    });
  });
});

describe('getAvailableMeetingActions', () => {
  const noAccess = resolveMeetingPermissions(false, false);
  const viewOnly = resolveMeetingPermissions(true, false);
  const edit = resolveMeetingPermissions(false, true);

  it('returns no actions while permissions are loading', () => {
    expect(getAvailableMeetingActions(meeting('RUNNING'), { ...edit, isLoading: true })).toEqual(
      [],
    );
  });

  it('returns no actions without View or Edit Meeting', () => {
    expect(getAvailableMeetingActions(meeting('SCHEDULED'), noAccess)).toEqual([]);
  });

  it('gates scheduled meeting actions', () => {
    expect(getAvailableMeetingActions(meeting('SCHEDULED'), viewOnly)).toEqual(['VIEW_DETAIL']);
    expect(getAvailableMeetingActions(meeting('SCHEDULED'), edit)).toEqual([
      'VIEW_DETAIL',
      'EDIT',
      'START',
      'CANCEL',
    ]);
  });

  it('gates running meeting actions', () => {
    expect(getAvailableMeetingActions(meeting('RUNNING'), viewOnly)).toEqual([
      'JOIN',
      'VIEW_DETAIL',
    ]);
    expect(getAvailableMeetingActions(meeting('RUNNING'), edit)).toEqual([
      'JOIN',
      'VIEW_DETAIL',
      'END',
    ]);
  });

  it.each(['COMPLETED', 'CANCELED'] as const)(
    'only exposes details and history for %s meetings',
    (status) => {
      expect(getAvailableMeetingActions(meeting(status), viewOnly)).toEqual([
        'VIEW_DETAIL',
        'VIEW_HISTORY',
      ]);
      expect(getAvailableMeetingActions(meeting(status), edit)).toEqual([
        'VIEW_DETAIL',
        'VIEW_HISTORY',
      ]);
    },
  );
});
