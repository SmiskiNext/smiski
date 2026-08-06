/**
 * Opens meeting details above Jira rather than inside the Issue Panel iframe.
 *
 * The dialog loads its participant roster from the backend inside that
 * iframe, so it carries the numeric Jira identifiers published to
 * `api/backendContext.ts`.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { getBackendContext } from '../api/backendContext';
import type { Meeting } from '../domain';
import {
    MEETING_DETAIL_MODAL_KIND,
    type MeetingDetailModalContext,
} from '../utils/issuePanelModalContext';

export function useIssuePanelMeetingDetailModal() {
    const open = (meeting: Meeting) => {
        const context: MeetingDetailModalContext = {
            kind: MEETING_DETAIL_MODAL_KIND,
            meeting,
            ...getBackendContext(),
        };
        new ForgeModal({ context, size: 'medium' }).open();
    };

    return { open };
}
