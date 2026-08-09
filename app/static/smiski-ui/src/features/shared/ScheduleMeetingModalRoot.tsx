/**
 * Mounted inside the Forge platform Modal iframe opened by
 * `hooks/useIssuePanelScheduleModal.ts`. Creates use ScheduleMeetingModal;
 * edits use the shared status-aware EditMeetingModal. Both report back to the
 * opener (a different iframe, with its own React Query cache) via
 * `view.close(result)`.
 */
import { view } from '@forge/bridge';
import {
    EditMeetingModal,
    ScheduleMeetingModal,
} from '../../components/shared';
import type {
    ScheduleMeetingModalContext,
    ScheduleMeetingModalResult,
} from '../../utils/scheduleMeetingModalContext';

export interface ScheduleMeetingModalRootProps {
    payload: ScheduleMeetingModalContext;
}

export function ScheduleMeetingModalRoot({
    payload,
}: ScheduleMeetingModalRootProps) {
    const close = (result: ScheduleMeetingModalResult) => {
        void view.close(result);
    };

    if (payload.meeting) {
        return (
            <EditMeetingModal
                isOpen
                meetingId={payload.meeting.id}
                chrome='embedded'
                onClose={() => close({ submitted: false })}
                onSaved={() =>
                    close({ submitted: true, meetingId: payload.meeting?.id })
                }
            />
        );
    }

    return (
        <ScheduleMeetingModal
            isOpen
            chrome='embedded'
            issueKey={payload.issueKey}
            issueId={payload.issueId}
            projectKey={payload.projectKey}
            onClose={() => close({ submitted: false })}
            onSubmitted={(meetingId) => close({ submitted: true, meetingId })}
        />
    );
}
