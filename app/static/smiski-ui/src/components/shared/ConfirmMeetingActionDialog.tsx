import type { Meeting } from '../../domain';
import type { ConfirmableMeetingAction } from '../../utils/issuePanelModalContext';
import { ConfirmActionDialog } from './ConfirmActionDialog';

export interface ConfirmMeetingActionDialogProps {
    action: ConfirmableMeetingAction;
    meeting: Meeting;
    isLoading?: boolean;
    error?: string | null;
    onConfirm: () => void;
    onClose: () => void;
    chrome?: 'overlay' | 'embedded';
}

const COPY: Record<
    ConfirmableMeetingAction,
    {
        title: string;
        confirmLabel: string;
        describe: (meeting: Meeting) => string;
    }
> = {
    CANCEL: {
        title: 'Cancel meeting?',
        confirmLabel: 'Cancel meeting',
        describe: (meeting) =>
            `This cancels "${meeting.title}" and notifies its invitees. This can't be undone.`,
    },
    END: {
        title: 'End meeting?',
        confirmLabel: 'End meeting',
        describe: (meeting) =>
            `This ends "${meeting.title}" for everyone in the room right now.`,
    },
    DELETE: {
        title: 'Delete meeting?',
        confirmLabel: 'Delete meeting',
        describe: (meeting) =>
            `This removes "${meeting.title}" from Smiski and schedules it for permanent deletion.`,
    },
};

/** Meeting-action-specific copy over the generic `ConfirmActionDialog`. */
export function ConfirmMeetingActionDialog({
    action,
    meeting,
    isLoading,
    error,
    onConfirm,
    onClose,
    chrome,
}: ConfirmMeetingActionDialogProps) {
    const copy = COPY[action];
    return (
        <ConfirmActionDialog
            title={copy.title}
            message={copy.describe(meeting)}
            confirmLabel={copy.confirmLabel}
            isLoading={isLoading}
            error={error}
            onConfirm={onConfirm}
            onClose={onClose}
            chrome={chrome}
        />
    );
}
