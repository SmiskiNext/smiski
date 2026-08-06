/**
 * Opens meeting details above Jira rather than inside the Issue Panel iframe.
 * Standalone Vite development cannot create Forge platform modals, so callers
 * receive a local fallback meeting to render with the same shared dialog.
 *
 * The dialog loads its participant roster from the backend inside that
 * iframe, so it carries the numeric Jira identifiers published to
 * `api/backendContext.ts`.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { useState } from 'react';
import { getBackendContext } from '../api/backendContext';
import type { Meeting } from '../domain';
import {
    MEETING_DETAIL_MODAL_KIND,
    type MeetingDetailModalContext,
} from '../utils/issuePanelModalContext';

export function useIssuePanelMeetingDetailModal() {
    const [devMeeting, setDevMeeting] = useState<Meeting | null>(null);

    const open = (meeting: Meeting) => {
        if (import.meta.env.DEV) {
            setDevMeeting(meeting);
            return;
        }

        const context: MeetingDetailModalContext = {
            kind: MEETING_DETAIL_MODAL_KIND,
            meeting,
            ...getBackendContext(),
        };
        new ForgeModal({ context, size: 'medium' }).open();
    };

    return {
        open,
        devMeeting,
        closeDev: () => setDevMeeting(null),
    };
}
