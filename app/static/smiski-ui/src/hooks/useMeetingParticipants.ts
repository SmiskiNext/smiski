/**
 * useMeetingParticipants — participant roster for a meeting.
 * Swap point: replace `queryFn` with `api.getMeetingParticipants(meetingId)`.
 */
import { useQuery } from '@tanstack/react-query';
import { useCurrentUser } from '../context/CurrentUserContext';
import { listMeetingParticipants } from '../mocks/db';
import { resolveParticipantDisplayNames } from '../mocks/participants';
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
            ? queryKeys.participants(meetingId)
            : ['participants', 'none'],
        queryFn: () => listMeetingParticipants(meetingId as string),
        enabled: Boolean(meetingId),
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
