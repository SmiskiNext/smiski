import type { Meeting, MeetingPermissions } from './meeting';

export type MeetingAction =
    | 'VIEW_DETAIL'
    | 'VIEW_HISTORY'
    | 'JOIN'
    | 'START'
    | 'EDIT'
    | 'CANCEL'
    | 'END'
    | 'SETTINGS';

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

/**
 * Pure action policy — mirrors the backend `meet` service's actual
 * authorization exactly: `EDIT`/`CANCEL`/`END` require the acting user to be
 * the meeting's host (`hostId.equals(actor)`), not merely hold the project-level
 * `Edit Meeting` permission. `START`/`JOIN` have no host restriction
 * backend-side, so those stay gated on `canEditMeeting`/`canViewMeeting` alone.
 * `EDIT` is available to the host in every status (`SCHEDULED`, `RUNNING`,
 * `COMPLETED`, `CANCELED`) and consolidates settings and invitee management for
 * the project page (no separate `SETTINGS` action). The domain-layer
 * `updateInfo` accepts info changes in all statuses and rejects time/zone
 * changes off-SCHEDULED; `updateSettings` rejects COMPLETED/CANCELED.
 *
 * `JOIN` is also offered on `SCHEDULED` meetings, but only to users without
 * `Edit Meeting`: they cannot start the meeting, so `JOIN` is their only way
 * in — it opens the meeting room's waiting room, which holds the room-token
 * request back until the host starts the meeting. Users who can edit keep
 * `START` instead, since starting it is the more direct action for them.
 */
export function getAvailableMeetingActions(
    meeting: Meeting,
    permissions: MeetingPermissions,
    currentUserAccountId: string,
): MeetingAction[] {
    if (permissions.isLoading || !permissions.canViewMeeting) return [];
    const isHost = meeting.hostId === currentUserAccountId;

    switch (meeting.status) {
        case 'SCHEDULED': {
            if (!permissions.canEditMeeting) return ['JOIN', 'VIEW_DETAIL'];
            const actions: MeetingAction[] = ['VIEW_DETAIL'];
            if (isHost) actions.push('EDIT');
            actions.push('START');
            if (isHost) actions.push('CANCEL');
            return actions;
        }
        case 'RUNNING': {
            if (!permissions.canEditMeeting) return ['JOIN', 'VIEW_DETAIL'];
            const actions: MeetingAction[] = ['JOIN', 'VIEW_DETAIL'];
            if (isHost) {
                actions.push('EDIT');
                actions.push('END');
            }
            return actions;
        }
        case 'COMPLETED':
        case 'CANCELED': {
            const actions: MeetingAction[] = ['VIEW_DETAIL', 'VIEW_HISTORY'];
            if (permissions.canEditMeeting && isHost) actions.push('EDIT');
            return actions;
        }
    }
}
