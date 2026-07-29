/**
 * Opens the Start-instant-meeting form as a full-screen Forge platform modal
 * (`@forge/bridge`'s Modal) so it renders over the whole product window instead
 * of squeezed inside the narrow Issue Panel iframe. Standalone `vite dev` has
 * no Forge bridge, so it falls back to the local in-page
 * <StartInstantMeetingModal> (isDevOpen/devPayload) instead — see call sites.
 * Mirrors `hooks/useIssuePanelScheduleModal.ts`.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
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
    const [devPayload, setDevPayload] =
        useState<OpenInstantMeetingPayload | null>(null);
    const queryClient = useQueryClient();

    const open = (payload: OpenInstantMeetingPayload) => {
        if (import.meta.env.DEV) {
            setDevPayload(payload);
            return;
        }
        const context: InstantMeetingModalContext = {
            kind: INSTANT_MEETING_MODAL_KIND,
            ...payload,
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

    return {
        open,
        isDevOpen: devPayload !== null,
        devPayload,
        closeDev: () => setDevPayload(null),
    };
}
