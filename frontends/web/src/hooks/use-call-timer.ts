'use client';

import { useEffect, useRef, useState } from 'react';

type CallTimerResult = {
    formattedDuration: string;
    elapsedSeconds: number;
};

function formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainingSeconds = seconds % 60;

    const mm = String(minutes).padStart(2, '0');
    const ss = String(remainingSeconds).padStart(2, '0');

    if (hours > 0) {
        return `${hours}:${mm}:${ss}`;
    }

    return `${mm}:${ss}`;
}

/**
 * Tracks elapsed call time from when the hook mounts and provides
 * a formatted duration string in MM:SS or H:MM:SS format.
 */
export function useCallTimer(): CallTimerResult {
    const [elapsedSeconds, setElapsedSeconds] = useState(0);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    useEffect(() => {
        intervalRef.current = setInterval(() => {
            setElapsedSeconds((prev) => prev + 1);
        }, 1000);

        return () => {
            if (intervalRef.current !== null) {
                clearInterval(intervalRef.current);
            }
        };
    }, []);

    return {
        elapsedSeconds,
        formattedDuration: formatDuration(elapsedSeconds),
    };
}
