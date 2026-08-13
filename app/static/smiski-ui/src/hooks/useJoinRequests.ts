/**
 * Host-side manual-admission data hooks.
 *
 * The list/decision APIs use the generated SDK over Forge Remote. Realtime
 * notifications use a browser-native external fetch to the notification
 * service because Forge Remote buffers response bodies. The list is fetched
 * once on mount and again when the host opens the pending panel; an
 * unavailable stream is therefore a gap until the next explicit refetch,
 * not an unhandled rejection.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef } from 'react';
import { subscribeToMeetingJoinRequests } from '../api/meetingEvents';
import {
    acceptPendingMeetingJoinRequests,
    declinePendingMeetingJoinRequests,
    listPendingMeetingJoinRequests,
} from '../api/meetings';
import {
    enrichPendingJoinRequestsPageAvatars,
    type JoinRequestDecision,
    type PendingJoinRequest,
    type PendingJoinRequestsPage,
    type PendingJoinRequestsPageParams,
    removePendingJoinRequestsFromPage,
    upsertPendingJoinRequestPage,
} from '../domain';
import { queryKeys } from './queryKeys';

export interface UsePendingJoinRequestsOptions
    extends PendingJoinRequestsPageParams {
    enabled?: boolean;
    realtime?: boolean;
    onNewJoinRequest?: (request: PendingJoinRequest) => void;
}

export function usePendingJoinRequests(
    meetingId?: string,
    options: UsePendingJoinRequestsOptions = {},
) {
    const {
        enabled = true,
        realtime = true,
        offset,
        pageSize,
        onNewJoinRequest,
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
    const onNewJoinRequestRef = useRef(onNewJoinRequest);
    useEffect(() => {
        onNewJoinRequestRef.current = onNewJoinRequest;
    }, [onNewJoinRequest]);

    useEffect(() => {
        if (!meetingId || !enabled || !realtime) return;
        const controller = new AbortController();
        subscribeToMeetingJoinRequests(meetingId, {
            signal: controller.signal,
            onJoinRequest: (request) => {
                let isNew = false;
                queryClient.setQueryData<PendingJoinRequestsPage>(
                    queryKey,
                    (page) => {
                        if (!page) return page;
                        isNew = !page.requests.some(
                            (candidate) =>
                                candidate.requestId === request.requestId,
                        );
                        return upsertPendingJoinRequestPage(page, request);
                    },
                );
                if (isNew) onNewJoinRequestRef.current?.(request);
            },
        }).catch(() => undefined);
        return () => controller.abort();
    }, [enabled, meetingId, queryClient, queryKey, realtime]);

    return useQuery({
        queryKey,
        queryFn: async () => {
            const previous =
                queryClient.getQueryData<PendingJoinRequestsPage>(queryKey);
            const page = await listPendingMeetingJoinRequests(
                meetingId as string,
                params,
            );
            return enrichPendingJoinRequestsPageAvatars(page, previous);
        },
        enabled: Boolean(meetingId) && enabled,
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
