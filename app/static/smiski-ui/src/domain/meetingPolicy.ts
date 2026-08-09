import type { Meeting, MeetingPermissions } from './meeting';

export type MeetingAction =
    | 'VIEW_DETAIL'
    | 'VIEW_HISTORY'
    | 'JOIN'
    | 'START'
    | 'EDIT'
    | 'CANCEL'
    | 'END'
    | 'DELETE';

/** Backend delete policy shared by menus and batch-selection controls. */
export function canDeleteMeeting(
    meeting: Meeting,
    permissions: Pick<MeetingPermissions, 'canEditMeeting' | 'isLoading'>,
    currentUserAccountId: string,
): boolean {
    return (
        !permissions.isLoading
        && permissions.canEditMeeting
        && meeting.hostId === currentUserAccountId
        && meeting.status !== 'RUNNING'
    );
}

export function getDeletableMeetingIds(
    meetings: Meeting[],
    permissions: Pick<MeetingPermissions, 'canEditMeeting' | 'isLoading'>,
    currentUserAccountId: string,
): Set<string> {
    return new Set(
        meetings
            .filter((meeting) =>
                canDeleteMeeting(meeting, permissions, currentUserAccountId),
            )
            .map((meeting) => meeting.id),
    );
}

/** Drops stale or newly ineligible IDs after a list refetch. */
export function pruneMeetingSelection(
    selectedIds: ReadonlySet<string>,
    deletableIds: ReadonlySet<string>,
): Set<string> {
    return new Set([...selectedIds].filter((id) => deletableIds.has(id)));
}

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
 * `Edit Meeting` permission. `START` is intentionally host-only in the UI;
 * non-hosts use `JOIN`, which accurately reflects manual admission behavior.
 * `EDIT` is available to the host in every status (`SCHEDULED`, `RUNNING`,
 * `COMPLETED`, `CANCELED`) and consolidates settings and invitee management for
 * both Jira surfaces. The domain-layer
 * `updateInfo` accepts info changes in all statuses and rejects time/zone
 * changes off-SCHEDULED; `updateSettings` rejects COMPLETED/CANCELED.
 *
 * `JOIN` is also offered on `SCHEDULED` meetings to every non-host. It opens
 * the meeting room's waiting room, which holds the room-token request back
 * until the host starts the meeting.
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
            if (!permissions.canEditMeeting || !isHost)
                return ['JOIN', 'VIEW_DETAIL'];
            const actions: MeetingAction[] = ['VIEW_DETAIL'];
            actions.push('EDIT');
            actions.push('START');
            actions.push('CANCEL');
            if (canDeleteMeeting(meeting, permissions, currentUserAccountId))
                actions.push('DELETE');
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
            if (canDeleteMeeting(meeting, permissions, currentUserAccountId))
                actions.push('DELETE');
            return actions;
        }
    }
}
