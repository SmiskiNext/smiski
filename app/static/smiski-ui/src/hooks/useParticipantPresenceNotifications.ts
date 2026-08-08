/**
 * useParticipantPresenceNotifications — the toast queue behind the meeting
 * room's "{name} joined" / "{name} left" notices.
 *
 * Feed `enqueue` to `useLiveKitRoom`'s `onParticipantPresence` and render
 * `toasts` through `ParticipantPresenceToasts`. Events describe other
 * participants only: LiveKit never raises its presence events for the local
 * user, so nothing here has to filter self out.
 *
 * The queue is intentionally shallow — at most
 * {@link MAX_VISIBLE_PRESENCE_TOASTS} are shown at once and each one expires on
 * its own timer — because a room that fills up quickly would otherwise bury the
 * video area under a column of notices.
 *
 * `enabled` is mirrored into a ref that `setEnabled` writes eagerly, so
 * `enqueue` keeps one stable identity for the room to hand to `useLiveKitRoom`
 * and still suppresses events raised in the same tick the toggle flips.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
    readPresenceNotificationsEnabled,
    writePresenceNotificationsEnabled,
} from '../utils/presenceNotificationPreference';
import type { ParticipantPresenceEvent } from './useLiveKitRoom';

/** Matches `InlineFeedback`'s default so both notice styles read the same. */
export const PRESENCE_TOAST_DURATION_MS = 4000;

export const MAX_VISIBLE_PRESENCE_TOASTS = 3;

export interface ParticipantPresenceToast extends ParticipantPresenceEvent {
    id: string;
}

/**
 * Appends a toast, dropping the oldest entries once the list would exceed
 * `cap`. Pure, so the capping rule is testable without timers.
 */
export function appendToast(
    toasts: ParticipantPresenceToast[],
    toast: ParticipantPresenceToast,
    cap = MAX_VISIBLE_PRESENCE_TOASTS,
): ParticipantPresenceToast[] {
    return [...toasts, toast].slice(-cap);
}

export interface UseParticipantPresenceNotificationsResult {
    toasts: ParticipantPresenceToast[];
    /** `false` suppresses every notice and clears whatever is on screen. */
    enabled: boolean;
    setEnabled: (enabled: boolean) => void;
    /** Wire this to `useLiveKitRoom`'s `onParticipantPresence`. */
    enqueue: (event: ParticipantPresenceEvent) => void;
    dismiss: (id: string) => void;
}

export function useParticipantPresenceNotifications(): UseParticipantPresenceNotificationsResult {
    const [toasts, setToasts] = useState<ParticipantPresenceToast[]>([]);
    const [enabled, setEnabledState] = useState(
        readPresenceNotificationsEnabled,
    );
    const timersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());
    const nextIdRef = useRef(0);
    const enabledRef = useRef(enabled);
    useEffect(() => {
        enabledRef.current = enabled;
    }, [enabled]);

    const clearTimer = useCallback((id: string) => {
        const timer = timersRef.current.get(id);
        if (timer === undefined) return;
        clearTimeout(timer);
        timersRef.current.delete(id);
    }, []);

    const clearAllTimers = useCallback(() => {
        for (const timer of timersRef.current.values()) clearTimeout(timer);
        timersRef.current.clear();
    }, []);

    useEffect(() => clearAllTimers, [clearAllTimers]);

    const dismiss = useCallback(
        (id: string) => {
            clearTimer(id);
            setToasts((current) => current.filter((toast) => toast.id !== id));
        },
        [clearTimer],
    );

    const enqueue = useCallback(
        (event: ParticipantPresenceEvent) => {
            if (!enabledRef.current) return;
            nextIdRef.current += 1;
            const id = `presence-${nextIdRef.current}`;
            setToasts((current) => appendToast(current, { ...event, id }));
            timersRef.current.set(
                id,
                setTimeout(() => dismiss(id), PRESENCE_TOAST_DURATION_MS),
            );
        },
        [dismiss],
    );

    const setEnabled = useCallback(
        (next: boolean) => {
            enabledRef.current = next;
            setEnabledState(next);
            writePresenceNotificationsEnabled(next);
            if (next) return;
            clearAllTimers();
            setToasts([]);
        },
        [clearAllTimers],
    );

    return { toasts, enabled, setEnabled, enqueue, dismiss };
}
