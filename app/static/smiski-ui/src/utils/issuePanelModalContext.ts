/**
 * Serializable payloads used to open Issue Panel dialogs in a Forge platform
 * modal. A platform modal owns a separate iframe above Jira, which prevents
 * these dialogs from being clipped to the narrow Issue Panel surface.
 */
import type { Meeting } from '../domain';

export const MEETING_DETAIL_MODAL_KIND = 'meeting-detail';
export const ACTIVE_MEETING_WARNING_MODAL_KIND = 'active-meeting-warning';
export const MEETING_SETTINGS_MODAL_KIND = 'meeting-settings';
export const CONFIRM_MEETING_ACTION_MODAL_KIND = 'confirm-meeting-action';

export interface MeetingDetailModalContext {
    kind: typeof MEETING_DETAIL_MODAL_KIND;
    meeting: Meeting;
}

export interface ActiveMeetingWarningModalContext {
    kind: typeof ACTIVE_MEETING_WARNING_MODAL_KIND;
    conflictingMeeting: Meeting;
}

export interface ActiveMeetingWarningModalResult {
    confirmed: boolean;
}

export interface MeetingSettingsModalContext {
    kind: typeof MEETING_SETTINGS_MODAL_KIND;
    meetingId: string;
}

export type ConfirmableMeetingAction = 'CANCEL' | 'END';

export interface ConfirmMeetingActionModalContext {
    kind: typeof CONFIRM_MEETING_ACTION_MODAL_KIND;
    action: ConfirmableMeetingAction;
    meeting: Meeting;
}

export interface ConfirmMeetingActionModalResult {
    confirmed: boolean;
}

export type IssuePanelModalContext =
    | MeetingDetailModalContext
    | ActiveMeetingWarningModalContext
    | MeetingSettingsModalContext
    | ConfirmMeetingActionModalContext;

export function isIssuePanelModalContext(
    value: unknown,
): value is IssuePanelModalContext {
    if (!value || typeof value !== 'object') return false;
    const kind = (value as { kind?: unknown }).kind;
    return (
        kind === MEETING_DETAIL_MODAL_KIND
        || kind === ACTIVE_MEETING_WARNING_MODAL_KIND
        || kind === MEETING_SETTINGS_MODAL_KIND
        || kind === CONFIRM_MEETING_ACTION_MODAL_KIND
    );
}
