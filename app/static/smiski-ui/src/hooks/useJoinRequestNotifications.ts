/**
 * useJoinRequestNotifications — the toast queue behind the host-only
 * "{name} is waiting to join" notices.
 *
 * Feed `enqueue` from `usePendingJoinRequests`'s `onNewJoinRequest` and render
 * `toasts` through `JoinRequestToasts`. Only genuinely new inserts should be
 * enqueued; replayed or merged events stay silent.
 *
 * The queue is intentionally shallow — at most
 * {@link MAX_VISIBLE_JOIN_REQUEST_TOASTS} are shown at once and each one
 * expires on its own timer — so a burst of requests cannot bury the video
 * area under a column of notices.
 */
import { useCallback, useEffect, useRef, useState } from 'react';

export const JOIN_REQUEST_TOAST_DURATION_MS = 4000;

export const MAX_VISIBLE_JOIN_REQUEST_TOASTS = 3;

export interface JoinRequestToast {
    id: string;
    requestId: string;
    displayName: string;
    avatarUrl: string;
}

export interface JoinRequestToastInput {
    requestId: string;
    displayName: string;
    avatarUrl?: string;
}

/**
 * Appends a toast, dropping the oldest entries once the list would exceed
 * `cap`. Pure, so the capping rule is testable without timers.
 */
export function appendJoinRequestToast(
    toasts: JoinRequestToast[],
    toast: JoinRequestToast,
    cap = MAX_VISIBLE_JOIN_REQUEST_TOASTS,
): JoinRequestToast[] {
    return [...toasts, toast].slice(-cap);
}

export interface UseJoinRequestNotificationsResult {
    toasts: JoinRequestToast[];
    enqueue: (request: JoinRequestToastInput) => void;
    dismiss: (id: string) => void;
    dismissByRequestId: (requestId: string) => void;
}

export function useJoinRequestNotifications(): UseJoinRequestNotificationsResult {
    const [toasts, setToasts] = useState<JoinRequestToast[]>([]);
    const timersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());
    const nextIdRef = useRef(0);

    const clearTimer = useCallback((id: string) => {
        const timer = timersRef.current.get(id);
        if (timer === undefined) return;
        clearTimeout(timer);
        timersRef.current.delete(id);
    }, []);

    const clearTimers = useCallback((ids: ReadonlyArray<string>) => {
        for (const id of ids) {
            const timer = timersRef.current.get(id);
            if (timer === undefined) continue;
            clearTimeout(timer);
            timersRef.current.delete(id);
        }
    }, []);

    useEffect(
        () => () => {
            for (const timer of timersRef.current.values()) clearTimeout(timer);
            timersRef.current.clear();
        },
        [],
    );

    const dismiss = useCallback(
        (id: string) => {
            clearTimer(id);
            setToasts((current) => current.filter((toast) => toast.id !== id));
        },
        [clearTimer],
    );

    const dismissByRequestId = useCallback(
        (requestId: string) => {
            setToasts((current) => {
                const removed = current.filter(
                    (toast) => toast.requestId === requestId,
                );
                clearTimers(removed.map((toast) => toast.id));
                return current.filter((toast) => toast.requestId !== requestId);
            });
        },
        [clearTimers],
    );

    const enqueue = useCallback(
        (request: JoinRequestToastInput) => {
            nextIdRef.current += 1;
            const id = `join-request-${nextIdRef.current}`;
            setToasts((current) =>
                appendJoinRequestToast(current, {
                    id,
                    requestId: request.requestId,
                    displayName: request.displayName,
                    avatarUrl: request.avatarUrl ?? '',
                }),
            );
            timersRef.current.set(
                id,
                setTimeout(() => dismiss(id), JOIN_REQUEST_TOAST_DURATION_MS),
            );
        },
        [dismiss],
    );

    return { toasts, enqueue, dismiss, dismissByRequestId };
}
