import { Modal as ForgeModal } from '@forge/bridge';
import { useState } from 'react';
import { ActiveMeetingWarningDialog } from '../../components/shared';
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
        issueKey: string;
        projectKey: string;
    }) => void;
}

export function StartInstantMeetingButton({
    issueKey,
    projectKey,
    disabled,
    className,
    onOpenInstantModal,
}: StartInstantMeetingButtonProps) {
    const [isConfirmOpen, setConfirmOpen] = useState(false);
    const { conflictingMeeting } = useHostConflict(issueKey);

    const openForm = () =>
        onOpenInstantModal(instantModalPayloadFor(issueKey, projectKey));

    const handleClick = () => {
        if (
            !conflictingMeeting
            || !shouldWarnBeforeInstant(conflictingMeeting)
        ) {
            openForm();
            return;
        }

        if (import.meta.env.DEV) {
            setConfirmOpen(true);
            return;
        }

        const context: ActiveMeetingWarningModalContext = {
            kind: ACTIVE_MEETING_WARNING_MODAL_KIND,
            conflictingMeeting,
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
        <>
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
            {import.meta.env.DEV && isConfirmOpen && conflictingMeeting && (
                <ActiveMeetingWarningDialog
                    conflictingMeeting={conflictingMeeting}
                    onClose={() => setConfirmOpen(false)}
                    onConfirm={() => {
                        setConfirmOpen(false);
                        openForm();
                    }}
                />
            )}
        </>
    );
}
