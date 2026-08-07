/**
 * Project-level Jira custom `View Meeting`/`Edit Meeting` permissions
 * (`manifest.yml`'s `jira:projectPermission` module) used by every meeting
 * action, read directly from the browser — see `api/meetingPermission.ts`.
 *
 * Two queries rather than one, because the two Jira reads behind this answer go
 * stale for unrelated reasons. Resolving the app's permission keys depends only
 * on which bundle is deployed, so it is cached site-wide under a build-scoped
 * key and shared by every project and every user. Checking whether this user
 * holds those keys depends on the project's permission scheme, so it stays
 * scoped per user and per project on a short expiry. Splitting them means
 * opening a second project pays for one read, not two.
 *
 * Loading is reported from `isPending` rather than `isLoading`: the check is
 * held by `skipToken` until the keys arrive, and a held query is pending but not
 * fetching. `isLoading` would read as settled during that gap and briefly render
 * the surface as "no permissions".
 *
 * UI gating only — the `meet` backend does not yet re-check this itself. See
 * app/AGENTS.md for the researched follow-up mechanism.
 */
import { skipToken, useQuery } from '@tanstack/react-query';
import {
    checkMeetingPermission,
    MEETING_PERMISSION_CHECK_STALE_TIME_MS,
    MEETING_PERMISSION_KEYS_STALE_TIME_MS,
    resolveMeetingPermissionKeys,
} from '../api/meetingPermission';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { MeetingPermissions } from '../domain';
import { resolveMeetingPermissions } from '../domain';
import { queryKeys } from './queryKeys';
import { PERSISTED_QUERY_GC_TIME_MS } from './queryPersistence';

export function useMeetingPermissions(projectKey: string): MeetingPermissions {
    const { accountId } = useCurrentUser();

    const keysQuery = useQuery({
        queryKey: queryKeys.meetingPermissionKeys(),
        queryFn: resolveMeetingPermissionKeys,
        staleTime: MEETING_PERMISSION_KEYS_STALE_TIME_MS,
        gcTime: PERSISTED_QUERY_GC_TIME_MS,
    });

    const keys = keysQuery.data;
    const checkQuery = useQuery({
        queryKey: queryKeys.meetingPermissions(projectKey, accountId),
        queryFn: keys
            ? () => checkMeetingPermission(projectKey, accountId, keys)
            : skipToken,
        staleTime: MEETING_PERMISSION_CHECK_STALE_TIME_MS,
        gcTime: PERSISTED_QUERY_GC_TIME_MS,
    });

    const error = keysQuery.error ?? checkQuery.error;

    return resolveMeetingPermissions(
        checkQuery.data?.hasViewMeeting ?? false,
        checkQuery.data?.hasEditMeeting ?? false,
        !error && (keysQuery.isPending || checkQuery.isPending),
        error,
    );
}
