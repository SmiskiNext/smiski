/**
 * useHostConflict — is the current user already hosting a RUNNING meeting on
 * a *different* issue? Backs the UC-01 alt flow in StartInstantMeetingButton
 * (confirm before starting a second concurrent meeting).
 */
import { useQuery } from '@tanstack/react-query';
import { findRunningMeetingHostedByUser } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import { resolveMeetingHostNames } from '../domain';
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

    // The result is always hosted by the current user (the SDK `list` call
    // filters `creatorId: accountId`), and the `list` summary shape carries
    // no host display name — resolve it from the identity already in hand
    // instead of leaving the mapper's placeholder.
    const conflictingMeeting = query.data
        ? resolveMeetingHostNames([query.data], [currentUser])[0]
        : null;

    return {
        conflictingMeeting,
        loading: query.isLoading,
    };
}
