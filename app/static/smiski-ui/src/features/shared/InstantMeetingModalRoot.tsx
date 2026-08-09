/**
 * Mounted inside the Forge platform Modal iframe opened by
 * `hooks/useIssuePanelInstantModal.ts`. Renders the same
 * StartInstantMeetingModal used everywhere else, but reports back to the opener
 * (a different iframe, with its own React Query cache) via `view.close(result)`
 * instead of a local onClose/onStarted callback.
 *
 * Ant Design theming comes from the app-root `ConfigProvider` in `App.tsx`,
 * which wraps this root too (this component is rendered from App's
 * `surface === 'modal'` branch). Mirrors `features/shared/ScheduleMeetingModalRoot.tsx`.
 */
import { view } from '@forge/bridge';
import { StartInstantMeetingModal } from '../../components/shared';
import type {
    InstantMeetingModalContext,
    InstantMeetingModalResult,
} from '../../utils/instantMeetingModalContext';

export interface InstantMeetingModalRootProps {
    payload: InstantMeetingModalContext;
}

export function InstantMeetingModalRoot({
    payload,
}: InstantMeetingModalRootProps) {
    const close = (result: InstantMeetingModalResult) => {
        void view.close(result);
    };

    const projectKey =
        payload.projectKey
        ?? (payload.issueKey ? payload.issueKey.split('-')[0] : '');

    return (
        <StartInstantMeetingModal
            isOpen
            chrome='embedded'
            issueKey={payload.issueKey}
            issueId={payload.issueId}
            projectKey={projectKey}
            onClose={() => close({ created: false })}
            onStarted={(meetingId) => close({ created: true, meetingId })}
        />
    );
}
