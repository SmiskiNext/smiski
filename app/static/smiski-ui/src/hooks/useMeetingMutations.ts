/**
 * Meeting write operations (create/schedule/update/cancel/start), as React
 * Query mutations over the mock in-memory db. Each invalidates the query
 * caches a change could affect, so lists refresh immediately.
 *
 * Swap point for real wiring: replace each `mutationFn` with the matching
 * `api/meetings.ts` function (same signature) once the Kong Gateway
 * integration exists.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type {
    CreateInstantMeetingInput,
    ScheduleMeetingInput,
    UpdateMeetingInput,
} from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
import type { ProjectMember } from '../domain';
import * as mockDb from '../mocks/db';
import { queryKeys } from './queryKeys';

function useInvalidateMeetings() {
    const queryClient = useQueryClient();
    return () => {
        queryClient.invalidateQueries({ queryKey: ['meetings'] });
        queryClient.invalidateQueries({ queryKey: ['meeting'] });
        queryClient.invalidateQueries({ queryKey: ['host-conflict'] });
    };
}

/**
 * Captures the real Jira actor plus the project-member directory already
 * loaded by the meeting form. The mock database can then persist readable
 * names while retaining the same API payload contracts used by the backend.
 */
function useMockIdentityContext() {
    const currentUser = useCurrentUser();
    const queryClient = useQueryClient();

    return (issueKey: string): mockDb.MockMeetingIdentityContext => {
        const projectKey = issueKey.split('-')[0];
        const projectMembers =
            queryClient.getQueryData<ProjectMember[]>(
                queryKeys.projectMembers(projectKey),
            ) ?? [];
        return { currentUser, projectMembers };
    };
}

export function useCreateInstantMeeting() {
    const invalidate = useInvalidateMeetings();
    const identityForIssue = useMockIdentityContext();
    return useMutation({
        mutationFn: (input: CreateInstantMeetingInput) =>
            mockDb.createInstantMeeting(
                input,
                identityForIssue(input.issueKey),
            ),
        onSuccess: invalidate,
    });
}

export function useScheduleMeeting() {
    const invalidate = useInvalidateMeetings();
    const identityForIssue = useMockIdentityContext();
    return useMutation({
        mutationFn: (input: ScheduleMeetingInput) =>
            mockDb.scheduleMeeting(input, identityForIssue(input.issueKey)),
        onSuccess: invalidate,
    });
}

export function useUpdateMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: ({
            meetingId,
            input,
        }: {
            meetingId: string;
            input: UpdateMeetingInput;
        }) => mockDb.updateMeeting(meetingId, input),
        onSuccess: invalidate,
    });
}

export function useCancelMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (meetingId: string) => mockDb.cancelMeeting(meetingId),
        onSuccess: invalidate,
    });
}

export function useStartMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (meetingId: string) => mockDb.startMeeting(meetingId),
        onSuccess: invalidate,
    });
}

export function useEndMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (meetingId: string) => mockDb.endMeeting(meetingId),
        onSuccess: invalidate,
    });
}
