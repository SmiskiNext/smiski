/**
 * Opens the Schedule/Edit meeting form as a full-screen Forge platform modal
 * (`@forge/bridge`'s Modal) so it renders over the whole product window
 * instead of squeezed inside the narrow Issue Panel iframe. Standalone
 * `vite dev` has no Forge bridge, so it falls back to the local in-page
 * <ScheduleMeetingModal> (isDevOpen/devPayload) instead — see call sites.
 *
 * The modal iframe cannot observe the originating issue, so the numeric Jira
 * identifiers this surface published to `api/backendContext.ts` are copied
 * into the payload and republished on the other side.
 */

import { Modal as ForgeModal } from '@forge/bridge';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getBackendContext } from '../api/backendContext';
import type { Meeting } from '../domain';
import {
    SCHEDULE_MEETING_MODAL_KIND,
    type ScheduleMeetingModalContext,
    type ScheduleMeetingModalResult,
} from '../utils/scheduleMeetingModalContext';

export interface OpenScheduleMeetingPayload {
    issueKey?: string;
    projectKey?: string;
    meeting?: Meeting;
}

export function useIssuePanelScheduleModal(
    onSubmitted?: (meetingId: string) => void,
) {
    const [devPayload, setDevPayload] =
        useState<OpenScheduleMeetingPayload | null>(null);
    const queryClient = useQueryClient();

    const open = (payload: OpenScheduleMeetingPayload) => {
        if (import.meta.env.DEV) {
            setDevPayload(payload);
            return;
        }
        const context: ScheduleMeetingModalContext = {
            kind: SCHEDULE_MEETING_MODAL_KIND,
            ...payload,
            ...getBackendContext(),
        };
        new ForgeModal({
            context,
            size: 'large',
            onClose: (result: ScheduleMeetingModalResult | undefined) => {
                if (result?.submitted) {
                    queryClient.invalidateQueries({ queryKey: ['meetings'] });
                    if (result.meetingId) onSubmitted?.(result.meetingId);
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
