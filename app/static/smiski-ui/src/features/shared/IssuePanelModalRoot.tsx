/**
 * Content root mounted inside Forge platform modal iframes opened from the
 * Issue Panel. Each dialog uses embedded chrome because Jira already provides
 * the outer modal container and backdrop.
 */
import { view } from '@forge/bridge';
import {
    ActiveMeetingWarningDialog,
    ConfirmMeetingActionDialog,
    MeetingDetailDialog,
    MeetingSettingsModal,
} from '../../components/shared';
import { useMeetingParticipants } from '../../hooks/useMeetingParticipants';
import {
    ACTIVE_MEETING_WARNING_MODAL_KIND,
    type ActiveMeetingWarningModalResult,
    CONFIRM_MEETING_ACTION_MODAL_KIND,
    type ConfirmMeetingActionModalResult,
    type IssuePanelModalContext,
    MEETING_SETTINGS_MODAL_KIND,
    type MeetingDetailModalContext,
} from '../../utils/issuePanelModalContext';

export interface IssuePanelModalRootProps {
    payload: IssuePanelModalContext;
}

export function IssuePanelModalRoot({ payload }: IssuePanelModalRootProps) {
    if (payload.kind === ACTIVE_MEETING_WARNING_MODAL_KIND) {
        const close = (result: ActiveMeetingWarningModalResult) => {
            void view.close(result);
        };

        return (
            <ActiveMeetingWarningDialog
                conflictingMeeting={payload.conflictingMeeting}
                chrome='embedded'
                onClose={() => close({ confirmed: false })}
                onConfirm={() => close({ confirmed: true })}
            />
        );
    }

    if (payload.kind === MEETING_SETTINGS_MODAL_KIND) {
        return (
            <MeetingSettingsModal
                isOpen
                meetingId={payload.meetingId}
                chrome='embedded'
                onClose={() => void view.close()}
                onSaved={() => void view.close()}
            />
        );
    }

    if (payload.kind === CONFIRM_MEETING_ACTION_MODAL_KIND) {
        const close = (result: ConfirmMeetingActionModalResult) => {
            void view.close(result);
        };

        return (
            <ConfirmMeetingActionDialog
                action={payload.action}
                meeting={payload.meeting}
                chrome='embedded'
                onClose={() => close({ confirmed: false })}
                onConfirm={() => close({ confirmed: true })}
            />
        );
    }

    return <MeetingDetailModalContent payload={payload} />;
}

function MeetingDetailModalContent({
    payload,
}: {
    payload: MeetingDetailModalContext;
}) {
    const { participants, loading } = useMeetingParticipants(
        payload.meeting.id,
        payload.meeting.projectKey,
    );

    return (
        <MeetingDetailDialog
            meeting={payload.meeting}
            participants={participants}
            isLoading={loading}
            chrome='embedded'
            onClose={() => void view.close()}
        />
    );
}
