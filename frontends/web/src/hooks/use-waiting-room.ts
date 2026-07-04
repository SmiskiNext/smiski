'use client';

import { EventSourcePolyfill } from 'event-source-polyfill';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import {
    approveAllJoinRequests,
    approveJoinRequest,
    denyJoinRequest,
    listJoinRequests,
} from '@/generated/sdk.gen.ts';
import type { MeetingManagementJoinRequestResponse } from '@/generated/types.gen.ts';
import { getApiBaseUrl } from '@/lib/api/client.ts';
import { getAccessToken } from '@/lib/auth/cookies.ts';

type WaitingRoomState = {
    requests: MeetingManagementJoinRequestResponse[];
    isLoading: boolean;
    error: string | null;
};

type UseWaitingRoomOptions = {
    onOpenSheet?: () => void;
};

type UseWaitingRoomResult = {
    requests: MeetingManagementJoinRequestResponse[];
    pendingCount: number;
    isLoading: boolean;
    error: string | null;
    approve: (requestId: string) => Promise<void>;
    deny: (requestId: string) => Promise<void>;
    approveAll: () => Promise<void>;
    refresh: () => void;
};

const JOIN_REQUEST_EVENTS = [
    'join_request_created',
    'join_request_expired',
] as const;
const SSE_RETRY_DELAY_MS = 3000;
const BATCH_TOAST_THRESHOLD = 3;
const BATCH_TOAST_ID = 'join-request-batch';

/**
 * Manages host-facing waiting room state including pending join requests,
 * approve/deny/approve-all mutations, SSE-driven live updates, and toast
 * notifications when new requests arrive.
 *
 * The list endpoint is the authoritative source of truth; SSE events trigger
 * targeted refetches rather than purely event-sourced local state.
 */
export function useWaitingRoom(
    meetingId: string | null,
    options?: UseWaitingRoomOptions,
): UseWaitingRoomResult {
    const t = useTranslations('meetingRoom');
    const onOpenSheet = options?.onOpenSheet;
    const onOpenSheetRef = useRef(onOpenSheet);
    onOpenSheetRef.current = onOpenSheet;

    const [state, setState] = useState<WaitingRoomState>({
        requests: [],
        isLoading: false,
        error: null,
    });

    const sseRef = useRef<EventSourcePolyfill | null>(null);
    const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const isMountedRef = useRef(true);
    const previousPendingIdsRef = useRef<Set<string>>(new Set());
    const activeToastIdsRef = useRef<Set<string>>(new Set());
    const isBatchToastActiveRef = useRef(false);
    const initialSnapshotTakenRef = useRef(false);

    const dismissAllToasts = useCallback(() => {
        activeToastIdsRef.current.forEach((id) => {
            toast.dismiss(id);
        });
        activeToastIdsRef.current.clear();
        if (isBatchToastActiveRef.current) {
            toast.dismiss(BATCH_TOAST_ID);
            isBatchToastActiveRef.current = false;
        }
    }, []);

    const loadRequests = useCallback(() => {
        if (!meetingId) return;

        setState((prev) => ({ ...prev, isLoading: true, error: null }));

        listJoinRequests({ path: { id: meetingId } })
            .then(({ data }) => {
                if (!isMountedRef.current) return;
                setState({
                    requests: data?.content ?? [],
                    isLoading: false,
                    error: null,
                });
            })
            .catch(() => {
                if (!isMountedRef.current) return;
                setState((prev) => ({
                    ...prev,
                    isLoading: false,
                    error: 'waitingRoomLoadError',
                }));
            });
    }, [meetingId]);

    const closeSse = useCallback(() => {
        if (retryTimerRef.current !== null) {
            clearTimeout(retryTimerRef.current);
            retryTimerRef.current = null;
        }
        if (sseRef.current) {
            sseRef.current.close();
            sseRef.current = null;
        }
    }, []);

    const openSse = useCallback(async () => {
        if (!meetingId) return;
        closeSse();

        const token = await getAccessToken();
        if (!token) return;
        if (!isMountedRef.current) return;

        const es = new EventSourcePolyfill(
            `${getApiBaseUrl()}/api/v1/meetings/${meetingId}/events`,
            { headers: { Authorization: `Bearer ${token}` } },
        );
        sseRef.current = es;

        const handleJoinRequestEvent = () => {
            if (isMountedRef.current) {
                loadRequests();
            }
        };

        JOIN_REQUEST_EVENTS.forEach((eventType) => {
            es.addEventListener(eventType, handleJoinRequestEvent);
        });

        es.onerror = () => {
            closeSse();
            if (isMountedRef.current) {
                retryTimerRef.current = setTimeout(() => {
                    if (isMountedRef.current) {
                        void openSse();
                        loadRequests();
                    }
                }, SSE_RETRY_DELAY_MS);
            }
        };
    }, [meetingId, closeSse, loadRequests]);

    useEffect(() => {
        isMountedRef.current = true;
        if (meetingId) {
            loadRequests();
            void openSse();
        }
        return () => {
            isMountedRef.current = false;
            closeSse();
            dismissAllToasts();
            previousPendingIdsRef.current = new Set();
            initialSnapshotTakenRef.current = false;
        };
    }, [meetingId, loadRequests, openSse, closeSse, dismissAllToasts]);

    const remove = useCallback((requestId: string) => {
        setState((prev) => ({
            ...prev,
            requests: prev.requests.filter((r) => r.id !== requestId),
        }));
    }, []);

    const approve = useCallback(
        async (requestId: string) => {
            if (!meetingId) return;
            remove(requestId);
            try {
                await approveJoinRequest({
                    path: { id: meetingId, requestId },
                    throwOnError: true,
                });
                loadRequests();
            } catch {
                loadRequests();
            }
        },
        [meetingId, remove, loadRequests],
    );

    const deny = useCallback(
        async (requestId: string) => {
            if (!meetingId) return;
            remove(requestId);
            try {
                await denyJoinRequest({
                    path: { id: meetingId, requestId },
                    throwOnError: true,
                });
                loadRequests();
            } catch {
                loadRequests();
            }
        },
        [meetingId, remove, loadRequests],
    );

    const approveAll = useCallback(async () => {
        if (!meetingId) return;
        setState((prev) => ({ ...prev, requests: [] }));
        try {
            await approveAllJoinRequests({
                path: { id: meetingId },
                throwOnError: true,
            });
            loadRequests();
        } catch {
            loadRequests();
        }
    }, [meetingId, loadRequests]);

    const showRequestToast = useCallback(
        (request: MeetingManagementJoinRequestResponse) => {
            const id = request.id;
            if (!id) return;
            const name = request.displayName ?? id;
            toast(t('joinRequestToastTitle', { name }), {
                id,
                description: t('joinRequestToastDescription'),
                duration: Number.POSITIVE_INFINITY,
                action: {
                    label: t('joinRequestApprove'),
                    onClick: () => {
                        void approve(id);
                    },
                },
                cancel: {
                    label: t('joinRequestDeny'),
                    onClick: () => {
                        void deny(id);
                    },
                },
                onDismiss: () => {
                    activeToastIdsRef.current.delete(id);
                },
                onAutoClose: () => {
                    activeToastIdsRef.current.delete(id);
                },
            });
            activeToastIdsRef.current.add(id);
        },
        [t, approve, deny],
    );

    const showBatchToast = useCallback(
        (count: number) => {
            const openSheet = onOpenSheetRef.current;
            toast(t('joinRequestBatchTitle', { count }), {
                id: BATCH_TOAST_ID,
                duration: Number.POSITIVE_INFINITY,
                action: openSheet
                    ? {
                          label: t('joinRequestBatchAction'),
                          onClick: () => {
                              openSheet();
                              toast.dismiss(BATCH_TOAST_ID);
                              isBatchToastActiveRef.current = false;
                          },
                      }
                    : undefined,
                onDismiss: () => {
                    isBatchToastActiveRef.current = false;
                },
            });
            isBatchToastActiveRef.current = true;
        },
        [t],
    );

    useEffect(() => {
        const pendingRequests = state.requests.filter(
            (r) => r.status === 'PENDING' && r.id,
        );
        const currentPendingIds = new Set<string>(
            pendingRequests.map((r) => r.id as string),
        );

        if (!initialSnapshotTakenRef.current) {
            if (!state.isLoading && state.error === null) {
                previousPendingIdsRef.current = currentPendingIds;
                initialSnapshotTakenRef.current = true;
            }
            return;
        }

        const previousIds = previousPendingIdsRef.current;
        const newRequests = pendingRequests.filter(
            (r) => r.id && !previousIds.has(r.id),
        );
        const resolvedIds: string[] = [];
        previousIds.forEach((id) => {
            if (!currentPendingIds.has(id)) resolvedIds.push(id);
        });

        resolvedIds.forEach((id) => {
            if (activeToastIdsRef.current.has(id)) {
                toast.dismiss(id);
                activeToastIdsRef.current.delete(id);
            }
        });

        const pendingCount = currentPendingIds.size;

        if (pendingCount >= BATCH_TOAST_THRESHOLD) {
            activeToastIdsRef.current.forEach((id) => {
                toast.dismiss(id);
            });
            activeToastIdsRef.current.clear();
            if (newRequests.length > 0 || !isBatchToastActiveRef.current) {
                showBatchToast(pendingCount);
            }
        } else {
            if (isBatchToastActiveRef.current) {
                toast.dismiss(BATCH_TOAST_ID);
                isBatchToastActiveRef.current = false;
            }
            newRequests.forEach(showRequestToast);
        }

        previousPendingIdsRef.current = currentPendingIds;
    }, [
        state.requests,
        state.isLoading,
        state.error,
        showRequestToast,
        showBatchToast,
    ]);

    const pendingCount = state.requests.filter(
        (r) => r.status === 'PENDING',
    ).length;

    return {
        requests: state.requests,
        pendingCount,
        isLoading: state.isLoading,
        error: state.error,
        approve,
        deny,
        approveAll,
        refresh: loadRequests,
    };
}
