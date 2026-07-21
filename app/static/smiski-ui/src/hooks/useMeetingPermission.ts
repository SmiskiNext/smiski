import { useQuery } from '@tanstack/react-query';
import type { MeetingPermissions } from '../domain';
import { resolveMeetingPermissions } from '../domain';
import { useCurrentUser } from '../context/CurrentUserContext';

interface RawMeetingPermissions {
  hasViewMeeting: boolean;
  hasEditMeeting: boolean;
}

/**
 * Project-level Jira custom permissions used by every meeting action.
 * Replace the mock query with the Forge/Jira permission endpoint; consumers
 * stay unchanged because inheritance is normalized here.
 */
export function useMeetingPermissions(projectKey = 'SMISKI'): MeetingPermissions {
  const currentUser = useCurrentUser();
  const query = useQuery<RawMeetingPermissions>({
    queryKey: ['meeting-permissions', projectKey, currentUser.accountId],
    queryFn: async () => {
      await new Promise((resolve) => setTimeout(resolve, 180));
      return {
        hasViewMeeting: true,
        // Frontend-only prototype: keep both permissions enabled for the real
        // invoking Jira user until project permission lookup is implemented.
        hasEditMeeting: true,
      };
    },
    staleTime: Number.POSITIVE_INFINITY,
  });

  return resolveMeetingPermissions(
    query.data?.hasViewMeeting ?? false,
    query.data?.hasEditMeeting ?? false,
    query.isLoading,
    query.error as Error | null,
  );
}
