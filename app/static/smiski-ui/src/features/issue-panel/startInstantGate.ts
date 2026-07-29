/**
 * Pure gating helpers for the Issue Panel "Start instant" entry. Extracted from
 * `StartInstantMeetingButton` so the conflict-gate decision and the prefilled
 * modal payload are unit-testable without React, `@forge/bridge`, or a
 * QueryClient. The button composes these with the host-conflict query and the
 * platform-modal transport.
 */
import type { Meeting } from '../../domain';
import type { OpenInstantMeetingPayload } from '../../hooks/useIssuePanelInstantModal';

/**
 * The instant-modal payload for the current issue — the form opens prefilled
 * with this issue key, deriving the project key from it when not supplied.
 */
export function instantModalPayloadFor(
    issueKey: string,
    projectKey?: string,
): Required<Pick<OpenInstantMeetingPayload, 'issueKey' | 'projectKey'>> {
    return {
        issueKey,
        projectKey: projectKey ?? issueKey.split('-')[0],
    };
}

/**
 * Whether the active-meeting (host-conflict) warning must be shown before the
 * instant-meeting form. True only when the host already has a conflicting
 * running meeting.
 */
export function shouldWarnBeforeInstant(
    conflictingMeeting: Meeting | null,
): boolean {
    return conflictingMeeting !== null;
}
