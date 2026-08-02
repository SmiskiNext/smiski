/**
 * Host-side manual-admission data hooks.
 *
 * The list/decision APIs use the generated SDK over Forge Remote. Realtime
 * notifications use a browser-native external fetch to the notification
 * service because Forge `requestRemote` buffers response bodies. Polling stays
 * enabled as a recovery path when the stream is unavailable.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { subscribeToMeetingJoinRequests } from '../api/meetingEvents';
import {
    acceptPendingMeetingJoinRequests,
    declinePendingMeetingJoinRequests,
    listPendingMeetingJoinRequests,
} from '../api/meetings';
import type { PendingJoinRequestsPageParams } from '../domain';
import { queryKeys } from './queryKeys';

export const DEFAULT_JOIN_REQUEST_POLL_INTERVAL_MS = 5_000;

export interface UsePendingJoinRequestsOptions
    extends PendingJoinRequestsPageParams {
    enabled?: boolean;
    pollIntervalMs?: number | false;
    realtime?: boolean;
}

export function usePendingJoinRequests(
    meetingId?: string,
    options: UsePendingJoinRequestsOptions = {},
) {
    const {
        enabled = true,
        pollIntervalMs = DEFAULT_JOIN_REQUEST_POLL_INTERVAL_MS,
        realtime = true,
        offset,
        pageSize,
    } = options;
    const params = { offset, pageSize };
    const queryClient = useQueryClient();

    useEffect(() => {
        if (!meetingId || !enabled || !realtime || import.meta.env.DEV) return;
        const controller = new AbortController();
        void subscribeToMeetingJoinRequests(meetingId, {
            signal: controller.signal,
            onJoinRequest: () => {
                void queryClient.invalidateQueries({
                    queryKey: queryKeys.pendingJoinRequests(meetingId),
                });
            },
        });
        return () => controller.abort();
    }, [enabled, meetingId, queryClient, realtime]);

    return useQuery({
        queryKey: meetingId
            ? queryKeys.pendingJoinRequestsPage(meetingId, params)
            : ['meeting', 'none', 'join-requests', 'pending'],
        queryFn: () =>
            listPendingMeetingJoinRequests(meetingId as string, params),
        enabled: Boolean(meetingId) && enabled,
        refetchInterval: pollIntervalMs,
        refetchIntervalInBackground: false,
    });
}

interface DecideJoinRequestsInput {
    meetingId: string;
    requestIds: string[];
}

function useInvalidatePendingJoinRequests() {
    const queryClient = useQueryClient();
    return (meetingId: string) =>
        queryClient.invalidateQueries({
            queryKey: queryKeys.pendingJoinRequests(meetingId),
        });
}

export function useAcceptJoinRequests() {
    const invalidate = useInvalidatePendingJoinRequests();
    return useMutation({
        mutationFn: ({ meetingId, requestIds }: DecideJoinRequestsInput) =>
            acceptPendingMeetingJoinRequests(meetingId, requestIds),
        onSuccess: (_result, { meetingId }) => invalidate(meetingId),
    });
}

export function useDeclineJoinRequests() {
    const invalidate = useInvalidatePendingJoinRequests();
    return useMutation({
        mutationFn: ({ meetingId, requestIds }: DecideJoinRequestsInput) =>
            declinePendingMeetingJoinRequests(meetingId, requestIds),
        onSuccess: (_result, { meetingId }) => invalidate(meetingId),
    });
}
