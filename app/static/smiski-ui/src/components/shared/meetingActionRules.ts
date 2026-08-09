import type { MeetingAction } from '../../domain';

const ACTION_LABELS: Record<MeetingAction, string> = {
    VIEW_DETAIL: 'View details',
    VIEW_HISTORY: 'View history',
    JOIN: 'Join',
    START: 'Start',
    EDIT: 'Edit',
    CANCEL: 'Cancel',
    END: 'End meeting',
    SETTINGS: 'Settings',
    DELETE: 'Delete',
};

export function actionLabel(action: MeetingAction): string {
    return ACTION_LABELS[action];
}

export type { MeetingAction } from '../../domain';
