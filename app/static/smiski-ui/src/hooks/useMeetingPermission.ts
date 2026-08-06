import { useQuery } from '@tanstack/react-query';
import { getMeetingPermission } from '../api/meetingPermission';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { MeetingPermissions } from '../domain';
import { resolveMeetingPermissions } from '../domain';

interface RawMeetingPermissions {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
}

/**
 * Project-level Jira custom `View Meeting`/`Edit Meeting` permissions
 * (`manifest.yml`'s `jira:projectPermission` module) used by every meeting
 * action. Resolved from Jira's `mypermissions` check, called directly from the
 * browser — see `api/meetingPermission.ts`.
 *
 * UI gating only — the `meet` backend does not yet re-check this itself. See
 * app/AGENTS.md for the researched follow-up mechanism.
 */
export function useMeetingPermissions(projectKey: string): MeetingPermissions {
    const currentUser = useCurrentUser();
    const query = useQuery<RawMeetingPermissions>({
        queryKey: ['meeting-permissions', projectKey, currentUser.accountId],
        queryFn: () => getMeetingPermission(projectKey),
        staleTime: Number.POSITIVE_INFINITY,
    });

    return resolveMeetingPermissions(
        query.data?.hasViewMeeting ?? false,
        query.data?.hasEditMeeting ?? false,
        query.isLoading,
        query.error as Error | null,
    );
}
