/**
 * Opens meeting settings as a Forge platform modal, mirroring
 * `useIssuePanelMeetingDetailModal` — standalone Vite development has no
 * Forge bridge, so it falls back to local in-page state instead.
 *
 * The modal reads and writes meeting settings from its own iframe, so it
 * carries the numeric Jira identifiers published to `api/backendContext.ts`.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { useState } from 'react';
import { getBackendContext } from '../api/backendContext';
import {
    MEETING_SETTINGS_MODAL_KIND,
    type MeetingSettingsModalContext,
} from '../utils/issuePanelModalContext';

export function useIssuePanelSettingsModal() {
    const [devMeetingId, setDevMeetingId] = useState<string | null>(null);

    const open = (meetingId: string) => {
        if (import.meta.env.DEV) {
            setDevMeetingId(meetingId);
            return;
        }

        const context: MeetingSettingsModalContext = {
            kind: MEETING_SETTINGS_MODAL_KIND,
            meetingId,
            ...getBackendContext(),
        };
        new ForgeModal({ context, size: 'medium' }).open();
    };

    return {
        open,
        devMeetingId,
        closeDev: () => setDevMeetingId(null),
    };
}
