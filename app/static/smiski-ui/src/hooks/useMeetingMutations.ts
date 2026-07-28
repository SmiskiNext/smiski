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
import { createInstantMeeting, scheduleMeeting } from '../api/meetings';
import * as mockDb from '../mocks/db';

function useInvalidateMeetings() {
    const queryClient = useQueryClient();
    return () => {
        queryClient.invalidateQueries({ queryKey: ['meetings'] });
        queryClient.invalidateQueries({ queryKey: ['meeting'] });
        queryClient.invalidateQueries({ queryKey: ['host-conflict'] });
    };
}

/**
 * Create an instant meeting against the real `meet` backend (via the Forge
 * resolver). Unlike schedule/update/cancel/start/end below, this flow does NOT
 * fall back to the in-memory mock — a backend failure surfaces to the caller
 * (BREAKING; standalone `vite dev` cannot create instant meetings).
 */
export function useCreateInstantMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (input: CreateInstantMeetingInput) =>
            createInstantMeeting(input),
        onSuccess: invalidate,
    });
}

/**
 * Create a scheduled meeting against the real `meet` backend (via Forge Remote).
 * Like the instant flow, this does NOT fall back to the in-memory mock — a
 * backend failure surfaces to the caller (BREAKING; standalone `vite dev` cannot
 * create scheduled meetings). The edit branch below stays on the mock.
 */
export function useScheduleMeeting() {
    const invalidate = useInvalidateMeetings();
    return useMutation({
        mutationFn: (input: ScheduleMeetingInput) => scheduleMeeting(input),
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
