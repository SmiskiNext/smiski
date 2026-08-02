/**
 * useMeetingParticipants — participant roster for a meeting.
 *
 * Every call site already fetches the same meeting's detail via `useMeeting`,
 * and the backend `get` response already embeds the participant list — so
 * this shares `useMeeting`'s query (same key + fetcher) via `select` instead
 * of issuing a second, redundant request.
 */
import { useQuery } from '@tanstack/react-query';
import { getMeeting } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import { resolveParticipantDisplayNames } from '../domain';
import { queryKeys } from './queryKeys';
import { useProjectMembers } from './useProjectMembers';

export function useMeetingParticipants(
    meetingId?: string,
    projectKey?: string,
) {
    const currentUser = useCurrentUser();
    const projectMembers = useProjectMembers(projectKey);
    const query = useQuery({
        queryKey: meetingId
            ? queryKeys.meeting(meetingId)
            : ['meeting', 'none'],
        queryFn: () => getMeeting(meetingId as string),
        enabled: Boolean(meetingId),
        select: (data) => data.participants,
    });

    const participants = resolveParticipantDisplayNames(
        query.data ?? [],
        currentUser,
        projectMembers.members,
    );

    return {
        participants,
        loading:
            query.isLoading || (Boolean(projectKey) && projectMembers.loading),
        error: (query.error ?? projectMembers.error) as Error | null,
    };
}
