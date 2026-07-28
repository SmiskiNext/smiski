/**
 * Payload shape passed to/from the Start-instant-meeting form when it's opened
 * as a full-screen `@forge/bridge` platform Modal (see
 * `hooks/useIssuePanelInstantModal.ts` and `App.tsx`) — this lets the shared
 * modal escape the narrow Issue Panel iframe instead of rendering squeezed
 * inside it. Mirrors `utils/scheduleMeetingModalContext.ts`.
 */

export const INSTANT_MEETING_MODAL_KIND = 'instant-meeting';

export interface InstantMeetingModalContext {
    kind: typeof INSTANT_MEETING_MODAL_KIND;
    issueKey?: string;
    projectKey?: string;
}

export interface InstantMeetingModalResult {
    created: boolean;
    meetingId?: string;
}

export function isInstantMeetingModalContext(
    value: unknown,
): value is InstantMeetingModalContext {
    if (!value || typeof value !== 'object') return false;
    return (value as { kind?: unknown }).kind === INSTANT_MEETING_MODAL_KIND;
}
