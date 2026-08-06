/**
 * Opens meeting settings as a Forge platform modal, mirroring
 * `useIssuePanelMeetingDetailModal`.
 *
 * The modal reads and writes meeting settings from its own iframe, so it
 * carries the numeric Jira identifiers published to `api/backendContext.ts`.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { getBackendContext } from '../api/backendContext';
import {
    MEETING_SETTINGS_MODAL_KIND,
    type MeetingSettingsModalContext,
} from '../utils/issuePanelModalContext';

export function useIssuePanelSettingsModal() {
    const open = (meetingId: string) => {
        const context: MeetingSettingsModalContext = {
            kind: MEETING_SETTINGS_MODAL_KIND,
            meetingId,
            ...getBackendContext(),
        };
        new ForgeModal({ context, size: 'medium' }).open();
    };

    return { open };
}
