/**
 * Opens the Start-instant-meeting form as a full-screen Forge platform modal
 * (`@forge/bridge`'s Modal) so it renders over the whole product window instead
 * of squeezed inside the narrow Issue Panel iframe.
 * Mirrors `hooks/useIssuePanelScheduleModal.ts`, including copying the numeric
 * Jira identifiers from `api/backendContext.ts` into the modal payload.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { useQueryClient } from '@tanstack/react-query';
import { getBackendContext } from '../api/backendContext';
import {
    INSTANT_MEETING_MODAL_KIND,
    type InstantMeetingModalContext,
    type InstantMeetingModalResult,
} from '../utils/instantMeetingModalContext';

export interface OpenInstantMeetingPayload {
    issueKey?: string;
    projectKey?: string;
}

export function useIssuePanelInstantModal(
    onStarted?: (meetingId: string) => void,
) {
    const queryClient = useQueryClient();

    const open = (payload: OpenInstantMeetingPayload) => {
        const context: InstantMeetingModalContext = {
            kind: INSTANT_MEETING_MODAL_KIND,
            ...payload,
            ...getBackendContext(),
        };
        new ForgeModal({
            context,
            size: 'medium',
            onClose: (result: InstantMeetingModalResult | undefined) => {
                if (result?.created) {
                    queryClient.invalidateQueries({ queryKey: ['meetings'] });
                    if (result.meetingId) onStarted?.(result.meetingId);
                }
            },
        }).open();
    };

    return { open };
}
