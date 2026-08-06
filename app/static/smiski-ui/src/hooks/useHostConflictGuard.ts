/**
 * useHostConflictGuard — gates "start hosting a new Running meeting" behind a
 * confirmation step when the invoking user already hosts a different Running
 * meeting (PROJECT_CONTEXT.md §4.6.1 / UC-01 & UC-03 precondition: a user may
 * host at most one Running meeting at a time).
 *
 * Checked on demand (a plain API call, not a reactive query) because the
 * relevant issue isn't always known ahead of the click — e.g. the Project
 * Page's generic "Start meeting" button lets the user pick the issue inside
 * the form itself, so there's nothing to key a `useQuery` on until then. For
 * a fixed, already-known issue (starting a specific `SCHEDULED` meeting, or
 * `StartInstantMeetingButton`'s own reactive `useHostConflict`), either
 * approach works; this one stays consistent across both cases.
 *
 * The check is advisory: when it cannot be reached, `guard` treats the result
 * as "no conflict" and runs the action anyway, because an unreachable
 * conflict check must not block the action the user asked for.
 *
 * `presentation: 'platform-modal'` pops the confirmation out to a Forge
 * platform modal (for narrow Forge surfaces like the Issue Panel — mirrors
 * `StartInstantMeetingButton`'s existing convention); `'inline'` renders
 * `ActiveMeetingWarningDialog` directly (for surfaces with full-page room,
 * like the Project Page dashboard, which already renders its other dialogs
 * inline).
 */
import { Modal as ForgeModal } from '@forge/bridge';
import { useState } from 'react';
import { getBackendContext } from '../api/backendContext';
import { findRunningMeetingHostedByUser } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { Meeting } from '../domain';
import {
    ACTIVE_MEETING_WARNING_MODAL_KIND,
    type ActiveMeetingWarningModalContext,
    type ActiveMeetingWarningModalResult,
} from '../utils/issuePanelModalContext';

export function useHostConflictGuard(
    presentation: 'inline' | 'platform-modal' = 'inline',
) {
    const currentUser = useCurrentUser();
    const [pending, setPending] = useState<{
        conflictingMeeting: Meeting;
        action: () => void;
    } | null>(null);

    const guard = async (
        excludingIssueKey: string | undefined,
        action: () => void,
    ) => {
        const conflictingMeeting = await findRunningMeetingHostedByUser(
            currentUser.accountId,
            excludingIssueKey,
        ).catch(() => null);
        if (!conflictingMeeting) {
            action();
            return;
        }
        if (presentation === 'inline') {
            setPending({ conflictingMeeting, action });
            return;
        }
        const context: ActiveMeetingWarningModalContext = {
            kind: ACTIVE_MEETING_WARNING_MODAL_KIND,
            conflictingMeeting,
            ...getBackendContext(),
        };
        new ForgeModal({
            context,
            size: 'medium',
            onClose: (result: ActiveMeetingWarningModalResult | undefined) => {
                if (result?.confirmed) action();
            },
        }).open();
    };

    const confirm = () => {
        pending?.action();
        setPending(null);
    };
    const dismiss = () => setPending(null);

    return {
        guard,
        conflictingMeeting: pending?.conflictingMeeting ?? null,
        confirm,
        dismiss,
    };
}
