/**
 * Host-side manual-admission data hooks.
 *
 * The list/decision APIs use the generated SDK over Forge Remote. Realtime
 * notifications use a browser-native external fetch to the notification
 * service because Forge Remote buffers response bodies. REST polling
 * stays enabled at a low frequency to reconcile missed or stale events, so an
 * unavailable stream degrades to polling instead of surfacing as an unhandled
 * rejection.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo } from 'react';
import { subscribeToMeetingJoinRequests } from '../api/meetingEvents';
import {
    acceptPendingMeetingJoinRequests,
    declinePendingMeetingJoinRequests,
    listPendingMeetingJoinRequests,
} from '../api/meetings';
import {
    type JoinRequestDecision,
    type PendingJoinRequestsPage,
    type PendingJoinRequestsPageParams,
    removePendingJoinRequestsFromPage,
    upsertPendingJoinRequestPage,
} from '../domain';
import { queryKeys } from './queryKeys';

export const DEFAULT_JOIN_REQUEST_POLL_INTERVAL_MS = 60_000;

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
    const params = useMemo(() => ({ offset, pageSize }), [offset, pageSize]);
    const queryClient = useQueryClient();
    const queryKey = useMemo(
        () =>
            meetingId
                ? queryKeys.pendingJoinRequestsPage(meetingId, params)
                : (['meeting', 'none', 'join-requests', 'pending'] as const),
        [meetingId, params],
    );

    useEffect(() => {
        if (!meetingId || !enabled || !realtime) return;
        const controller = new AbortController();
        subscribeToMeetingJoinRequests(meetingId, {
            signal: controller.signal,
            onJoinRequest: (request) => {
                queryClient.setQueryData<PendingJoinRequestsPage>(
                    queryKey,
                    (page) =>
                        page
                            ? upsertPendingJoinRequestPage(page, request)
                            : page,
                );
            },
        }).catch(() => undefined);
        return () => controller.abort();
    }, [enabled, meetingId, queryClient, queryKey, realtime]);

    return useQuery({
        queryKey,
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

function useResolvePendingJoinRequests() {
    const queryClient = useQueryClient();
    return (meetingId: string, decisions: JoinRequestDecision[]) => {
        const resolvedRequestIds = new Set(
            decisions
                .filter((decision) => decision.status !== 'FAILED')
                .map((decision) => decision.requestId),
        );
        queryClient.setQueriesData<PendingJoinRequestsPage>(
            { queryKey: queryKeys.pendingJoinRequests(meetingId) },
            (page) =>
                page
                    ? removePendingJoinRequestsFromPage(
                          page,
                          resolvedRequestIds,
                      )
                    : page,
        );
        return queryClient.invalidateQueries({
            queryKey: queryKeys.pendingJoinRequests(meetingId),
        });
    };
}

export function useAcceptJoinRequests() {
    const resolve = useResolvePendingJoinRequests();
    return useMutation({
        mutationFn: ({ meetingId, requestIds }: DecideJoinRequestsInput) =>
            acceptPendingMeetingJoinRequests(meetingId, requestIds),
        onSuccess: (result, { meetingId }) => resolve(meetingId, result),
    });
}

export function useDeclineJoinRequests() {
    const resolve = useResolvePendingJoinRequests();
    return useMutation({
        mutationFn: ({ meetingId, requestIds }: DecideJoinRequestsInput) =>
            declinePendingMeetingJoinRequests(meetingId, requestIds),
        onSuccess: (result, { meetingId }) => resolve(meetingId, result),
    });
}
