/**
 * Payload shape passed to/from the Schedule/Edit meeting form when it's opened
 * as a full-screen `@forge/bridge` platform Modal (see
 * `hooks/useIssuePanelScheduleModal.ts` and `App.tsx`) — this lets it escape
 * the narrow Issue Panel iframe instead of rendering squeezed inside it.
 */
import type { Meeting } from '../domain';

export const SCHEDULE_MEETING_MODAL_KIND = 'schedule-meeting';

export interface ScheduleMeetingModalContext {
  kind: typeof SCHEDULE_MEETING_MODAL_KIND;
  issueKey?: string;
  projectKey?: string;
  meeting?: Meeting;
}

export interface ScheduleMeetingModalResult {
  submitted: boolean;
  meetingId?: string;
}
