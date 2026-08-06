/**
 * Gates CANCEL/END behind a confirmation step and reports the outcome —
 * mirrors `useHostConflictGuard`'s inline/platform-modal split: the Issue
 * Panel is a narrow iframe with no room for an overlay, so its confirmation
 * pops out to a Forge platform modal (`ConfirmMeetingActionDialog` rendered
 * by `IssuePanelModalRoot`); the Project Page dashboard renders the same
 * dialog inline, like its other dialogs.
 *
 * The two presentations differ in what feedback they can give on failure:
 * 'inline' keeps the dialog open and shows the mutation's own error so the
 * host can retry without re-opening the action menu. 'platform-modal' closes
 * as soon as the host confirms — the actual mutation runs after that, back
 * in this component's context — so a failure there can only be reported
 * asynchronously via `onDone`.
 */
import { Modal as ForgeModal } from '@forge/bridge';
import { useState } from 'react';
import { getBackendContext } from '../api/backendContext';
import type { Meeting } from '../domain';
import {
    CONFIRM_MEETING_ACTION_MODAL_KIND,
    type ConfirmableMeetingAction,
    type ConfirmMeetingActionModalContext,
    type ConfirmMeetingActionModalResult,
} from '../utils/issuePanelModalContext';
import { useCancelMeeting, useEndMeeting } from './useMeetingMutations';

export type { ConfirmableMeetingAction } from '../utils/issuePanelModalContext';

export interface ConfirmMeetingActionFeedback {
    appearance: 'success' | 'error';
    message: string;
}

function actionVerb(action: ConfirmableMeetingAction): string {
    return action === 'END' ? 'end' : 'cancel';
}

export function useConfirmMeetingAction(
    onDone: (feedback: ConfirmMeetingActionFeedback) => void,
    presentation: 'inline' | 'platform-modal' = 'inline',
) {
    const [pending, setPending] = useState<{
        action: ConfirmableMeetingAction;
        meeting: Meeting;
    } | null>(null);
    const cancelMeeting = useCancelMeeting();
    const endMeeting = useEndMeeting();
    const mutation = pending?.action === 'END' ? endMeeting : cancelMeeting;

    /** Fires the mutation without keeping any dialog open for its result. */
    const runDetached = (
        action: ConfirmableMeetingAction,
        meeting: Meeting,
    ) => {
        const mutate =
            action === 'END' ? endMeeting.mutate : cancelMeeting.mutate;
        mutate(meeting.id, {
            onSuccess: () =>
                onDone({
                    appearance: 'success',
                    message:
                        action === 'END'
                            ? 'Meeting ended.'
                            : 'Meeting canceled.',
                }),
            onError: (error) =>
                onDone({
                    appearance: 'error',
                    message: `Could not ${actionVerb(action)} the meeting: ${
                        error instanceof Error ? error.message : 'unknown error'
                    }`,
                }),
        });
    };

    const request = (action: ConfirmableMeetingAction, meeting: Meeting) => {
        if (presentation === 'inline' || import.meta.env.DEV) {
            cancelMeeting.reset();
            endMeeting.reset();
            setPending({ action, meeting });
            return;
        }

        const context: ConfirmMeetingActionModalContext = {
            kind: CONFIRM_MEETING_ACTION_MODAL_KIND,
            action,
            meeting,
            ...getBackendContext(),
        };
        new ForgeModal({
            context,
            size: 'small',
            onClose: (result: ConfirmMeetingActionModalResult | undefined) => {
                if (result?.confirmed) runDetached(action, meeting);
            },
        }).open();
    };

    /** Only used by the 'inline'/dev-fallback dialog — keeps it open on error. */
    const confirm = () => {
        if (!pending) return;
        const { action, meeting } = pending;
        const mutate =
            action === 'END' ? endMeeting.mutate : cancelMeeting.mutate;
        mutate(meeting.id, {
            onSuccess: () => {
                onDone({
                    appearance: 'success',
                    message:
                        action === 'END'
                            ? 'Meeting ended.'
                            : 'Meeting canceled.',
                });
                setPending(null);
            },
        });
    };

    const dismiss = () => {
        setPending(null);
        cancelMeeting.reset();
        endMeeting.reset();
    };

    return {
        pending,
        request,
        confirm,
        dismiss,
        isLoading: mutation.isPending,
        error: mutation.error instanceof Error ? mutation.error.message : null,
    };
}
