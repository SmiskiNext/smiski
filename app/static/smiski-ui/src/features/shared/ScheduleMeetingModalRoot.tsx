/**
 * Mounted inside the Forge platform Modal iframe opened by
 * `hooks/useIssuePanelScheduleModal.ts`. Renders the same ScheduleMeetingModal
 * used everywhere else, but reports back to the opener (a different iframe,
 * with its own React Query cache) via `view.close(result)` instead of a local
 * onClose/onSubmitted callback.
 */
import { view } from '@forge/bridge';
import { ScheduleMeetingModal } from '../../components/shared';
import type {
  ScheduleMeetingModalContext,
  ScheduleMeetingModalResult,
} from '../../utils/scheduleMeetingModalContext';

export interface ScheduleMeetingModalRootProps {
  payload: ScheduleMeetingModalContext;
}

export function ScheduleMeetingModalRoot({ payload }: ScheduleMeetingModalRootProps) {
  const close = (result: ScheduleMeetingModalResult) => {
    void view.close(result);
  };

  return (
    <ScheduleMeetingModal
      isOpen
      chrome="embedded"
      issueKey={payload.issueKey}
      projectKey={payload.projectKey}
      meeting={payload.meeting}
      onClose={() => close({ submitted: false })}
      onSubmitted={(meetingId) => close({ submitted: true, meetingId })}
    />
  );
}
