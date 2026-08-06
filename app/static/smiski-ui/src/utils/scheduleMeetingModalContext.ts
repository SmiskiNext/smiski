/**
 * Payload shape passed to/from the Schedule/Edit meeting form when it's opened
 * as a full-screen `@forge/bridge` platform Modal (see
 * `hooks/useIssuePanelScheduleModal.ts` and `App.tsx`) — this lets it escape
 * the narrow Issue Panel iframe instead of rendering squeezed inside it.
 *
 * `issueId`/`projectId` are the numeric Jira identifiers the gateway needs to
 * scope its permission check. They travel in the payload because the modal
 * renders in its own iframe, whose Forge context carries this payload rather
 * than the originating issue — see `api/backendContext.ts`.
 */
import type { Meeting } from '../domain';

export const SCHEDULE_MEETING_MODAL_KIND = 'schedule-meeting';

export interface ScheduleMeetingModalContext {
    kind: typeof SCHEDULE_MEETING_MODAL_KIND;
    issueKey?: string;
    projectKey?: string;
    issueId?: string;
    projectId?: string;
    meeting?: Meeting;
}

export interface ScheduleMeetingModalResult {
    submitted: boolean;
    meetingId?: string;
}
