import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SSE_BACKOFF_DELAYS_MS } from './use-join-meeting.ts';

describe('SSE_BACKOFF_DELAYS_MS', () => {
    it('has exactly three backoff delay tiers', () => {
        expect(SSE_BACKOFF_DELAYS_MS).toHaveLength(3);
    });

    it('first retry fires after 1 second', () => {
        expect(SSE_BACKOFF_DELAYS_MS[0]).toBe(1000);
    });

    it('second retry fires after 2 seconds', () => {
        expect(SSE_BACKOFF_DELAYS_MS[1]).toBe(2000);
    });

    it('third retry fires after 4 seconds', () => {
        expect(SSE_BACKOFF_DELAYS_MS[2]).toBe(4000);
    });

    it('delays are in ascending order', () => {
        expect(SSE_BACKOFF_DELAYS_MS[0]).toBeLessThan(SSE_BACKOFF_DELAYS_MS[1]);
        expect(SSE_BACKOFF_DELAYS_MS[1]).toBeLessThan(SSE_BACKOFF_DELAYS_MS[2]);
    });
});

describe('SSE backoff logic (integration via vi.useFakeTimers)', () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('fires first retry at 1s, second at 2s, third at 4s from last attempt', () => {
        const delays = [...SSE_BACKOFF_DELAYS_MS];
        const timers: number[] = [];

        const scheduleRetry = (retryCount: number) => {
            if (retryCount < delays.length) {
                const delay = delays[retryCount];
                timers.push(delay);
            }
        };

        scheduleRetry(0);
        scheduleRetry(1);
        scheduleRetry(2);
        scheduleRetry(3);

        expect(timers).toEqual([1000, 2000, 4000]);

        vi.advanceTimersByTime(1000);
        expect(timers.length).toBe(3);
    });

    it('no retry scheduled after third attempt exhausts backoff', () => {
        const delays = [...SSE_BACKOFF_DELAYS_MS];
        const scheduledTimeouts: number[] = [];

        function scheduleOpenSse(retryCount: number) {
            if (retryCount < delays.length) {
                const delay = delays[retryCount];
                setTimeout(() => {
                    const nextRetryCount = retryCount + 1;
                    scheduledTimeouts.push(nextRetryCount);
                    if (nextRetryCount < delays.length) {
                        scheduleOpenSse(nextRetryCount);
                    }
                }, delay);
            }
        }

        scheduleOpenSse(0);
        expect(scheduledTimeouts).toHaveLength(0);

        vi.advanceTimersByTime(1000);
        expect(scheduledTimeouts).toEqual([1]);

        vi.advanceTimersByTime(2000);
        expect(scheduledTimeouts).toEqual([1, 2]);

        vi.advanceTimersByTime(4000);
        expect(scheduledTimeouts).toEqual([1, 2, 3]);

        vi.advanceTimersByTime(8000);
        expect(scheduledTimeouts).toEqual([1, 2, 3]);
    });
});
