import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCallTimer } from './use-call-timer.ts';

describe('useCallTimer', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('starts at 00:00', () => {
        const { result } = renderHook(() => useCallTimer());
        expect(result.current.formattedDuration).toBe('00:00');
        expect(result.current.elapsedSeconds).toBe(0);
    });

    it('formats MM:SS for durations under one hour', () => {
        const { result } = renderHook(() => useCallTimer());

        act(() => {
            vi.advanceTimersByTime(90 * 1000);
        });

        expect(result.current.formattedDuration).toBe('01:30');
        expect(result.current.elapsedSeconds).toBe(90);
    });

    it('formats H:MM:SS for durations of one hour or more', () => {
        const { result } = renderHook(() => useCallTimer());

        act(() => {
            vi.advanceTimersByTime(3661 * 1000);
        });

        expect(result.current.formattedDuration).toBe('1:01:01');
        expect(result.current.elapsedSeconds).toBe(3661);
    });

    it('pads minutes and seconds to two digits', () => {
        const { result } = renderHook(() => useCallTimer());

        act(() => {
            vi.advanceTimersByTime(5 * 1000);
        });

        expect(result.current.formattedDuration).toBe('00:05');
    });

    it('increments every second', () => {
        const { result } = renderHook(() => useCallTimer());

        act(() => {
            vi.advanceTimersByTime(3000);
        });

        expect(result.current.elapsedSeconds).toBe(3);
    });

    it('clears the interval on unmount', () => {
        const clearIntervalSpy = vi.spyOn(global, 'clearInterval');
        const { unmount } = renderHook(() => useCallTimer());

        unmount();

        expect(clearIntervalSpy).toHaveBeenCalled();
    });
});
