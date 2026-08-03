/**
 * Invitee reads and mutations share the meeting-detail cache because the meet
 * service embeds the active invitee list in its `get` response.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
    addMeetingInvitees,
    getMeeting,
    type MeetingInviteeInput,
    removeMeetingInvitees,
} from '../api/meetings';
import { queryKeys } from './queryKeys';

export function useMeetingInvitees(meetingId?: string) {
    const query = useQuery({
        queryKey: meetingId
            ? queryKeys.meeting(meetingId)
            : ['meeting', 'none'],
        queryFn: () => getMeeting(meetingId as string),
        enabled: Boolean(meetingId),
        select: (data) => data.invitees,
    });

    return {
        invitees: query.data ?? [],
        loading: query.isLoading,
        error: query.error as Error | null,
    };
}

interface AddMeetingInviteesInput {
    meetingId: string;
    invitees: MeetingInviteeInput[];
}

interface RemoveMeetingInviteesInput {
    meetingId: string;
    inviteeIds: string[];
}

function useInvalidateInvitees() {
    const queryClient = useQueryClient();
    return (meetingId: string) =>
        Promise.all([
            queryClient.invalidateQueries({
                queryKey: queryKeys.meeting(meetingId),
            }),
            queryClient.invalidateQueries({ queryKey: ['meetings'] }),
        ]);
}

export function useAddMeetingInvitees() {
    const invalidate = useInvalidateInvitees();
    return useMutation({
        mutationFn: ({ meetingId, invitees }: AddMeetingInviteesInput) =>
            addMeetingInvitees(meetingId, invitees),
        onSuccess: (_invitees, { meetingId }) => invalidate(meetingId),
    });
}

export function useRemoveMeetingInvitees() {
    const invalidate = useInvalidateInvitees();
    return useMutation({
        mutationFn: ({ meetingId, inviteeIds }: RemoveMeetingInviteesInput) =>
            removeMeetingInvitees(meetingId, inviteeIds),
        onSuccess: (_invitees, { meetingId }) => invalidate(meetingId),
    });
}
