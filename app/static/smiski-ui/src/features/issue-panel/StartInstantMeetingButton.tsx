import { Modal as ForgeModal } from '@forge/bridge';
import { getBackendContext } from '../../api/backendContext';
import { Button, Icon } from '../../components/ui';
import { useHostConflict } from '../../hooks/useHostConflict';
import {
    ACTIVE_MEETING_WARNING_MODAL_KIND,
    type ActiveMeetingWarningModalContext,
    type ActiveMeetingWarningModalResult,
} from '../../utils/issuePanelModalContext';
import {
    instantModalPayloadFor,
    shouldWarnBeforeInstant,
} from './startInstantGate';

export interface StartInstantMeetingButtonProps {
    issueId: string;
    issueKey: string;
    projectKey?: string;
    disabled?: boolean;
    className?: string;
    /**
     * Opens the shared instant-meeting form for this issue. Called only after
     * the host-conflict gate passes (no conflict, or the user confirmed the
     * active-meeting warning) — the meeting is never created without the form.
     */
    onOpenInstantModal: (payload: {
        issueId: string;
        issueKey: string;
        projectKey: string;
    }) => void;
}

export function StartInstantMeetingButton({
    issueId,
    issueKey,
    projectKey,
    disabled,
    className,
    onOpenInstantModal,
}: StartInstantMeetingButtonProps) {
    const { conflictingMeeting } = useHostConflict(issueKey);

    const openForm = () =>
        onOpenInstantModal(
            instantModalPayloadFor(issueId, issueKey, projectKey),
        );

    const handleClick = () => {
        if (
            !conflictingMeeting
            || !shouldWarnBeforeInstant(conflictingMeeting)
        ) {
            openForm();
            return;
        }

        const context: ActiveMeetingWarningModalContext = {
            kind: ACTIVE_MEETING_WARNING_MODAL_KIND,
            conflictingMeeting,
            ...getBackendContext(),
        };
        new ForgeModal({
            context,
            size: 'medium',
            onClose: (result: ActiveMeetingWarningModalResult | undefined) => {
                if (result?.confirmed) openForm();
            },
        }).open();
    };

    return (
        <Button
            variant='primary'
            size='sm'
            className={className}
            leadingIcon={<Icon name='video' size={15} />}
            onClick={handleClick}
            disabled={disabled}
        >
            Start instant
        </Button>
    );
}
