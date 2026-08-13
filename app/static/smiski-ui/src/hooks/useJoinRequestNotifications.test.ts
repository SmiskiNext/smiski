// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    appendJoinRequestToast,
    JOIN_REQUEST_TOAST_DURATION_MS,
    type JoinRequestToast,
    MAX_VISIBLE_JOIN_REQUEST_TOASTS,
    useJoinRequestNotifications,
} from './useJoinRequestNotifications';

function toast(id: string): JoinRequestToast {
    return {
        id,
        requestId: `request-${id}`,
        displayName: `User ${id}`,
        avatarUrl: '',
    };
}

describe('appendJoinRequestToast', () => {
    it('appends to an empty list', () => {
        expect(appendJoinRequestToast([], toast('1'))).toEqual([toast('1')]);
    });

    it('drops the oldest entry once the cap is exceeded', () => {
        const list = [toast('1'), toast('2'), toast('3')];

        expect(appendJoinRequestToast(list, toast('4'))).toEqual([
            toast('2'),
            toast('3'),
            toast('4'),
        ]);
    });

    it('defaults the cap to MAX_VISIBLE_JOIN_REQUEST_TOASTS', () => {
        const list = Array.from(
            { length: MAX_VISIBLE_JOIN_REQUEST_TOASTS },
            (_, index) => toast(String(index)),
        );

        expect(appendJoinRequestToast(list, toast('new'))).toHaveLength(
            MAX_VISIBLE_JOIN_REQUEST_TOASTS,
        );
    });
});

describe('useJoinRequestNotifications', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('enqueues a toast for a new request', () => {
        const { result } = renderHook(() => useJoinRequestNotifications());

        act(() => {
            result.current.enqueue({
                requestId: 'request-1',
                displayName: 'Alice',
                avatarUrl: 'https://avatar.example/alice.png',
            });
        });

        expect(result.current.toasts).toHaveLength(1);
        expect(result.current.toasts[0]).toMatchObject({
            requestId: 'request-1',
            displayName: 'Alice',
            avatarUrl: 'https://avatar.example/alice.png',
        });
    });

    it('caps visible toasts and drops the oldest', () => {
        const { result } = renderHook(() => useJoinRequestNotifications());

        act(() => {
            for (
                let index = 0;
                index < MAX_VISIBLE_JOIN_REQUEST_TOASTS + 2;
                index += 1
            ) {
                result.current.enqueue({
                    requestId: `request-${index}`,
                    displayName: `User ${index}`,
                });
            }
        });

        expect(result.current.toasts).toHaveLength(
            MAX_VISIBLE_JOIN_REQUEST_TOASTS,
        );
        expect(result.current.toasts.map((item) => item.displayName)).toEqual([
            'User 2',
            'User 3',
            'User 4',
        ]);
    });

    it('removes a toast on dismiss', () => {
        const { result } = renderHook(() => useJoinRequestNotifications());

        act(() => {
            result.current.enqueue({
                requestId: 'request-1',
                displayName: 'Alice',
            });
        });
        const [{ id }] = result.current.toasts;

        act(() => {
            result.current.dismiss(id);
        });

        expect(result.current.toasts).toHaveLength(0);
    });

    it('dismisses every toast for a decided request', () => {
        const { result } = renderHook(() => useJoinRequestNotifications());

        act(() => {
            result.current.enqueue({
                requestId: 'request-1',
                displayName: 'Alice',
            });
            result.current.enqueue({
                requestId: 'request-2',
                displayName: 'Bob',
            });
        });

        act(() => {
            result.current.dismissByRequestId('request-1');
        });

        expect(result.current.toasts.map((item) => item.requestId)).toEqual([
            'request-2',
        ]);
    });

    it('auto-dismisses a toast after the configured duration', () => {
        const { result } = renderHook(() => useJoinRequestNotifications());

        act(() => {
            result.current.enqueue({
                requestId: 'request-1',
                displayName: 'Alice',
            });
        });
        expect(result.current.toasts).toHaveLength(1);

        act(() => {
            vi.advanceTimersByTime(JOIN_REQUEST_TOAST_DURATION_MS);
        });

        expect(result.current.toasts).toHaveLength(0);
    });
});
