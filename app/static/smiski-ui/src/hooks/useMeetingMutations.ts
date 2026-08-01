/**
 * Meeting write operations (create/schedule/update/cancel/start/end). Each
 * invalidates the query caches a change could affect, so lists refresh
 * immediately. `useEndMeeting` calls the `endMeeting` resolver function,
 * which is an unimplemented stub — the real `meet` backend has no
 * host-initiated "end meeting" endpoint yet (see app/src/index.ts).
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiConfig } from '../api/config';
import { getDeviceId } from '../api/mappers';
import type {
    CreateInstantMeetingInput,
    ScheduleMeetingInput,
    UpdateMeetingInput,
} from '../api/meetings';
import {
    cancelMeeting,
    createInstantMeeting,
    endMeeting,
    joinMeeting,
    scheduleMeeting,
    updateMeeting,
} from '../api/meetings';
import { useCurrentUser } from '../context/CurrentUserContext';
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
 * Create an instant meeting against the real `meet` backend (via the generated
 * SDK over Forge Remote). Unlike schedule/update/cancel/start/end below, this
 * flow does NOT fall back to the in-memory mock. The mutation resolves with the
 * SDK-native `{ data, error }` result rather than throwing, so cache
 * invalidation runs only when `result.data` is present and the modal branches
 * on `result.error` (BREAKING; standalone `vite dev` cannot create instant
 * meetings).
 */
export function useCreateInstantMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (input: CreateInstantMeetingInput) =>
            createInstantMeeting(input),
        onSuccess: (result) => {
            if (result.data) invalidate();
        },
    });
}

/**
 * Create a scheduled meeting against the real `meet` backend (via the generated
 * SDK over Forge Remote). Like the instant flow, this does NOT fall back to the
 * in-memory mock and resolves with the SDK-native `{ data, error }` result, so
 * invalidation runs only when `result.data` is present (BREAKING; standalone
 * `vite dev` cannot create scheduled meetings). The edit branch below stays on
 * the mock.
 */
export function useScheduleMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (input: ScheduleMeetingInput) => scheduleMeeting(input),
        onSuccess: (result) => {
            if (result.data) invalidate();
        },
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
        }) => updateMeeting(meetingId, input),
        onSuccess: invalidate,
    });
}

export function useCancelMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (meetingId: string) => cancelMeeting(meetingId),
        onSuccess: invalidate,
    });
}

/**
 * "Start" a scheduled meeting by joining it as the host (backend `join`;
 * there is no separate start endpoint — joining is what transitions a
 * meeting to RUNNING). Seeds `useRoomToken`'s cache with the token/roomName
 * already returned here, so the meeting-room screen the caller navigates to
 * next doesn't re-request one.
 */
export function useStartMeeting() {
    const invalidate = useInvalidateMeetings();
    const queryClient = useQueryClient();
    const currentUser = useCurrentUser();
    return useMutation({
        mutationFn: (meetingId: string) =>
            joinMeeting(meetingId, {
                displayName: currentUser.displayName,
                deviceId: getDeviceId(),
                avatarUrl: currentUser.avatarUrl,
            }),
        onSuccess: (result, meetingId) => {
            queryClient.setQueryData(queryKeys.roomToken(meetingId), {
                token: result.token,
                url: apiConfig.liveKitUrl,
            });
            invalidate();
        },
    });
}

/**
 * Ends a RUNNING meeting (host/Edit-Meeting action). The real `meet`
 * backend has no equivalent endpoint (RUNNING→COMPLETED only happens there
 * via an async LiveKit webhook) — this demo branch implements it directly
 * in the resolver instead, since there is no backend to defer to.
 */
export function useEndMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (meetingId: string) => endMeeting(meetingId),
        onSuccess: invalidate,
    });
}
