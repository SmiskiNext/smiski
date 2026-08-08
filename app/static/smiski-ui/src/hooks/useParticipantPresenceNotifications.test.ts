// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    appendToast,
    MAX_VISIBLE_PRESENCE_TOASTS,
    type ParticipantPresenceToast,
    PRESENCE_TOAST_DURATION_MS,
    useParticipantPresenceNotifications,
} from './useParticipantPresenceNotifications';

const PREFERENCE_STORAGE_KEY = 'smiski:presence-notifications';

function toast(id: string): ParticipantPresenceToast {
    return { id, kind: 'joined', displayName: `User ${id}` };
}

describe('appendToast', () => {
    it('appends to an empty list', () => {
        expect(appendToast([], toast('1'))).toEqual([toast('1')]);
    });

    it('keeps every entry while under the cap', () => {
        const list = [toast('1'), toast('2')];

        expect(appendToast(list, toast('3'))).toEqual([
            toast('1'),
            toast('2'),
            toast('3'),
        ]);
    });

    it('drops the oldest entry once the cap is exceeded', () => {
        const list = [toast('1'), toast('2'), toast('3')];

        expect(appendToast(list, toast('4'))).toEqual([
            toast('2'),
            toast('3'),
            toast('4'),
        ]);
    });

    it('defaults the cap to MAX_VISIBLE_PRESENCE_TOASTS', () => {
        const list = Array.from(
            { length: MAX_VISIBLE_PRESENCE_TOASTS },
            (_, index) => toast(String(index)),
        );

        expect(appendToast(list, toast('new'))).toHaveLength(
            MAX_VISIBLE_PRESENCE_TOASTS,
        );
    });

    it('honors a custom cap', () => {
        expect(appendToast([toast('1')], toast('2'), 1)).toEqual([toast('2')]);
    });
});

describe('useParticipantPresenceNotifications', () => {
    beforeEach(() => {
        localStorage.clear();
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('defaults to enabled when no preference is stored', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        expect(result.current.enabled).toBe(true);
    });

    it('initializes from a previously stored preference', () => {
        localStorage.setItem(PREFERENCE_STORAGE_KEY, 'false');

        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        expect(result.current.enabled).toBe(false);
    });

    it('enqueues a toast while enabled', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.enqueue({ kind: 'joined', displayName: 'Alice' });
        });

        expect(result.current.toasts).toHaveLength(1);
        expect(result.current.toasts[0]).toMatchObject({
            kind: 'joined',
            displayName: 'Alice',
        });
    });

    it('does not enqueue while notifications are disabled', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.setEnabled(false);
        });
        act(() => {
            result.current.enqueue({ kind: 'joined', displayName: 'Alice' });
        });

        expect(result.current.toasts).toHaveLength(0);
    });

    it('caps visible toasts and drops the oldest', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            for (
                let index = 0;
                index < MAX_VISIBLE_PRESENCE_TOASTS + 2;
                index += 1
            ) {
                result.current.enqueue({
                    kind: 'joined',
                    displayName: `User ${index}`,
                });
            }
        });

        expect(result.current.toasts).toHaveLength(MAX_VISIBLE_PRESENCE_TOASTS);
        expect(result.current.toasts.map((item) => item.displayName)).toEqual([
            'User 2',
            'User 3',
            'User 4',
        ]);
    });

    it('removes a toast on dismiss', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.enqueue({ kind: 'joined', displayName: 'Alice' });
        });
        const [{ id }] = result.current.toasts;

        act(() => {
            result.current.dismiss(id);
        });

        expect(result.current.toasts).toHaveLength(0);
    });

    it('auto-dismisses a toast after the configured duration', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.enqueue({ kind: 'joined', displayName: 'Alice' });
        });
        expect(result.current.toasts).toHaveLength(1);

        act(() => {
            vi.advanceTimersByTime(PRESENCE_TOAST_DURATION_MS);
        });

        expect(result.current.toasts).toHaveLength(0);
    });

    it('persists the preference immediately when toggled', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.setEnabled(false);
        });

        expect(localStorage.getItem(PREFERENCE_STORAGE_KEY)).toBe('false');
    });

    it('suppresses an event raised in the same tick the toggle is turned off', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.setEnabled(false);
            result.current.enqueue({ kind: 'joined', displayName: 'Alice' });
        });

        expect(result.current.toasts).toHaveLength(0);
    });

    it('clears currently visible toasts when notifications are turned off', () => {
        const { result } = renderHook(() =>
            useParticipantPresenceNotifications(),
        );

        act(() => {
            result.current.enqueue({ kind: 'joined', displayName: 'Alice' });
        });
        expect(result.current.toasts).toHaveLength(1);

        act(() => {
            result.current.setEnabled(false);
        });

        expect(result.current.toasts).toHaveLength(0);
    });
});
