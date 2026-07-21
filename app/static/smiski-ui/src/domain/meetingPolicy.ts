import type { Meeting, MeetingPermissions } from './meeting';

export type MeetingAction =
  'VIEW_DETAIL' | 'VIEW_HISTORY' | 'JOIN' | 'START' | 'EDIT' | 'CANCEL' | 'END';

/**
 * Centralizes Jira custom-permission inheritance for the frontend.
 * EDIT_MEETING includes VIEW_MEETING at the business-policy layer even though
 * Jira does not automatically make one custom permission inherit the other.
 */
export function resolveMeetingPermissions(
  hasViewMeeting: boolean,
  hasEditMeeting: boolean,
  isLoading = false,
  error: Error | null = null,
): MeetingPermissions {
  return {
    hasViewMeeting,
    hasEditMeeting,
    canViewMeeting: hasViewMeeting || hasEditMeeting,
    canEditMeeting: hasEditMeeting,
    isLoading,
    error,
  };
}

/** Pure action policy. Backend authorization must enforce the same rules. */
export function getAvailableMeetingActions(
  meeting: Meeting,
  permissions: MeetingPermissions,
): MeetingAction[] {
  if (permissions.isLoading || !permissions.canViewMeeting) return [];

  switch (meeting.status) {
    case 'SCHEDULED':
      return permissions.canEditMeeting
        ? ['VIEW_DETAIL', 'EDIT', 'START', 'CANCEL']
        : ['VIEW_DETAIL'];
    case 'RUNNING':
      return permissions.canEditMeeting ? ['JOIN', 'VIEW_DETAIL', 'END'] : ['JOIN', 'VIEW_DETAIL'];
    case 'COMPLETED':
    case 'CANCELED':
      return ['VIEW_DETAIL', 'VIEW_HISTORY'];
  }
}
