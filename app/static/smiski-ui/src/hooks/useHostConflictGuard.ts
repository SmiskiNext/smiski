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
 * `presentation: 'platform-modal'` pops the confirmation out to a Forge
 * platform modal (for narrow Forge surfaces like the Issue Panel — mirrors
 * `StartInstantMeetingButton`'s existing convention); `'inline'` renders
 * `ActiveMeetingWarningDialog` directly (for surfaces with full-page room,
 * like the Project Page dashboard, which already renders its other dialogs
 * inline).
 */
import { Modal as ForgeModal } from '@forge/bridge';
import { useState } from 'react';
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
        // Standalone `vite dev` has no Forge bridge to reach the resolver
        // through (see `useHostConflict`'s equivalent fallback) — treat a
        // failed check as "no conflict" rather than silently swallowing the
        // action.
        const conflictingMeeting = await findRunningMeetingHostedByUser(
            currentUser.accountId,
            excludingIssueKey,
        ).catch(() => null);
        if (!conflictingMeeting) {
            action();
            return;
        }
        if (presentation === 'inline' || import.meta.env.DEV) {
            setPending({ conflictingMeeting, action });
            return;
        }
        const context: ActiveMeetingWarningModalContext = {
            kind: ACTIVE_MEETING_WARNING_MODAL_KIND,
            conflictingMeeting,
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
