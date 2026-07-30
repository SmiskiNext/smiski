import { useQuery } from '@tanstack/react-query';
import { getMeetingPermission } from '../api/meetingPermission';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { MeetingPermissions } from '../domain';
import { resolveMeetingPermissions } from '../domain';

interface RawMeetingPermissions {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
}

/** Standalone `vite dev` has no Forge bridge to invoke the resolver through. */
async function getMockMeetingPermission(): Promise<RawMeetingPermissions> {
    await new Promise((resolve) => setTimeout(resolve, 180));
    return { hasViewMeeting: true, hasEditMeeting: true };
}

/**
 * Project-level Jira custom `View Meeting`/`Edit Meeting` permissions
 * (`manifest.yml`'s `jira:projectPermission` module) used by every meeting
 * action. Real in a Forge context (resolver → `asUser().requestJira`'s
 * `mypermissions` check); mocked (always full access) in standalone
 * `vite dev`, which has no Forge bridge.
 *
 * UI gating only — the `meet` backend does not yet re-check this itself. See
 * app/AGENTS.md for the researched follow-up mechanism.
 */
export function useMeetingPermissions(
    projectKey = 'SMISKI',
): MeetingPermissions {
    const currentUser = useCurrentUser();
    const query = useQuery<RawMeetingPermissions>({
        queryKey: ['meeting-permissions', projectKey, currentUser.accountId],
        queryFn: () =>
            import.meta.env.DEV
                ? getMockMeetingPermission()
                : getMeetingPermission(projectKey),
        staleTime: Number.POSITIVE_INFINITY,
    });

    return resolveMeetingPermissions(
        query.data?.hasViewMeeting ?? false,
        query.data?.hasEditMeeting ?? false,
        query.isLoading,
        query.error as Error | null,
    );
}
