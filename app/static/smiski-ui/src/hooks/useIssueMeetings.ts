/**
 * useIssueMeetings — meetings bound to the current Issue (UC02/UC07), via the
 * backend `list` operation filtered by exact `issueKey`.
 *
 * The `list` response's meeting-summary shape carries no host display name
 * (only `hostId`), so `hostName`/`creatorName` are resolved against the Jira
 * project directory client-side — same approach `useMeetingParticipants` uses
 * for participants (`resolveParticipantDisplayNames`).
 */
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { listIssueMeetings } from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { Meeting } from '../domain';
import { resolveMeetingHostNames } from '../domain';
import { queryKeys } from './queryKeys';
import { useProjectMembers } from './useProjectMembers';

export interface UseIssueMeetingsResult {
    meetings: Meeting[];
    loading: boolean;
    error: Error | null;
    refresh: () => void;
}

export function useIssueMeetings(
    issueKey?: string,
    projectKey?: string,
    enabled = true,
): UseIssueMeetingsResult {
    const currentUser = useCurrentUser();
    const projectMembers = useProjectMembers(projectKey);
    const query = useQuery({
        queryKey: issueKey
            ? queryKeys.issueMeetings(issueKey)
            : ['meetings', 'issue', 'none'],
        queryFn: () => listIssueMeetings(issueKey as string),
        enabled: Boolean(issueKey) && enabled,
    });

    const meetings = useMemo(
        () =>
            resolveMeetingHostNames(query.data ?? [], [
                currentUser,
                ...projectMembers.members,
            ]),
        [query.data, currentUser, projectMembers.members],
    );

    return {
        meetings,
        loading: query.isLoading,
        error: query.error as Error | null,
        refresh: () => query.refetch(),
    };
}
