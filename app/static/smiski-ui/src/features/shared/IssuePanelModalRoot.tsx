/**
 * Content root mounted inside Forge platform modal iframes opened from the
 * Issue Panel. Each dialog uses embedded chrome because Jira already provides
 * the outer modal container and backdrop.
 */
import { view } from '@forge/bridge';
import { ActiveMeetingWarningDialog, MeetingDetailDialog } from '../../components/shared';
import { useMeetingParticipants } from '../../hooks/useMeetingParticipants';
import { MeetingRoom } from '../project-page/meeting-room/MeetingRoom';
import {
  ACTIVE_MEETING_WARNING_MODAL_KIND,
  MEETING_ROOM_MODAL_KIND,
  type ActiveMeetingWarningModalResult,
  type IssuePanelModalContext,
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
        chrome="embedded"
        onClose={() => close({ confirmed: false })}
        onConfirm={() => close({ confirmed: true })}
      />
    );
  }

  if (payload.kind === MEETING_ROOM_MODAL_KIND) {
    return (
      <MeetingRoom meetingId={payload.meetingId} onLeave={() => void view.close()} />
    );
  }

  return <MeetingDetailModalContent payload={payload} />;
}

function MeetingDetailModalContent({ payload }: { payload: MeetingDetailModalContext }) {
  const { participants, loading } = useMeetingParticipants(
    payload.meeting.id,
    payload.meeting.projectKey,
  );

  return (
    <MeetingDetailDialog
      meeting={payload.meeting}
      participants={participants}
      isLoading={loading}
      chrome="embedded"
      onClose={() => void view.close()}
    />
  );
}
