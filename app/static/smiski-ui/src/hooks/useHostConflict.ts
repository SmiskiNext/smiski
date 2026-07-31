/**
 * useHostConflict — is the current user already hosting a RUNNING meeting on
 * a *different* issue? Backs the UC-01 alt flow in StartInstantMeetingButton
 * (confirm before starting a second concurrent meeting).
 */
import { useQuery } from '@tanstack/react-query';
import { findRunningMeetingHostedByUser } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import { queryKeys } from './queryKeys';

export function useHostConflict(excludingIssueKey?: string) {
    const currentUser = useCurrentUser();
    const query = useQuery({
        queryKey: queryKeys.hostConflict(
            currentUser.accountId,
            excludingIssueKey,
        ),
        queryFn: () =>
            findRunningMeetingHostedByUser(
                currentUser.accountId,
                excludingIssueKey,
            ),
        enabled: Boolean(currentUser.accountId),
    });

    return {
        conflictingMeeting: query.data ?? null,
        loading: query.isLoading,
    };
}
