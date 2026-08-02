import type { Meeting, MeetingPermissions } from './meeting';

export type MeetingAction =
    | 'VIEW_DETAIL'
    | 'VIEW_HISTORY'
    | 'JOIN'
    | 'START'
    | 'EDIT'
    | 'CANCEL'
    | 'END';

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
 * authorization exactly (verified against
 * `CancelMeetingApplicationService`/`EndMeetingApplicationService`/
 * `UpdateMeetingApplicationService`): `EDIT`/`CANCEL`/`END` require the
 * acting user to be the meeting's host (`hostId.equals(actor)`), not merely
 * hold the project-level `Edit Meeting` permission — that permission only
 * gates *which* meetings a user can manage, not *whose*. `START`/`JOIN` have
 * no host restriction backend-side (`RequestJoinApplicationService` admits
 * any caller under the meeting's admission policy), so those stay gated on
 * `canEditMeeting`/`canViewMeeting` alone.
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
            if (!permissions.canEditMeeting) return ['VIEW_DETAIL'];
            const actions: MeetingAction[] = ['VIEW_DETAIL'];
            if (isHost) actions.push('EDIT');
            actions.push('START');
            if (isHost) actions.push('CANCEL');
            return actions;
        }
        case 'RUNNING': {
            if (!permissions.canEditMeeting) return ['JOIN', 'VIEW_DETAIL'];
            const actions: MeetingAction[] = ['JOIN', 'VIEW_DETAIL'];
            if (isHost) actions.push('END');
            return actions;
        }
        case 'COMPLETED':
        case 'CANCELED':
            return ['VIEW_DETAIL', 'VIEW_HISTORY'];
    }
}
