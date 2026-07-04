'use client';

import { useRoomContext } from '@livekit/components-react';
import { RoomEvent } from 'livekit-client';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
    startRecording as sdkStartRecording,
    stopRecording as sdkStopRecording,
} from '@/generated/sdk.gen.ts';

export type RecordingState = 'idle' | 'starting' | 'recording' | 'stopping';

type UseRecordingStateResult = {
    recordingState: RecordingState;
    error: string | null;
    startRecording: () => Promise<void>;
    stopRecording: () => Promise<void>;
    clearError: () => void;
};

type StableRecordingState = 'idle' | 'recording';

const METADATA_TIMEOUT_MS = 10_000;

function parseRecordingFromMetadata(
    metadata: string | undefined,
): boolean | null {
    if (!metadata) return null;
    try {
        const parsed = JSON.parse(metadata) as Record<string, unknown>;
        if (typeof parsed.recording === 'boolean') {
            return parsed.recording;
        }
        return null;
    } catch {
        return null;
    }
}

/**
 * Manages meeting recording state as a four-state machine anchored to
 * LiveKit room metadata. Handles API calls, metadata confirmation, 10-second
 * timeout fallback, and mount-safe state updates via isMountedRef.
 */
export function useRecordingState(
    meetingId: string | null,
): UseRecordingStateResult {
    const room = useRoomContext();
    const t = useTranslations('meetingRoom');

    const [recordingState, setRecordingState] =
        useState<RecordingState>('idle');
    const [error, setError] = useState<string | null>(null);

    const isMountedRef = useRef(true);
    const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const stableStateRef = useRef<StableRecordingState>('idle');

    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
            if (timeoutRef.current !== null) {
                clearTimeout(timeoutRef.current);
            }
        };
    }, []);

    const clearPendingTimeout = useCallback(() => {
        if (timeoutRef.current !== null) {
            clearTimeout(timeoutRef.current);
            timeoutRef.current = null;
        }
    }, []);

    const schedulePendingTimeout = useCallback(
        (timeoutErrorMessage: string) => {
            clearPendingTimeout();
            timeoutRef.current = setTimeout(() => {
                if (!isMountedRef.current) return;
                setRecordingState(stableStateRef.current);
                setError(timeoutErrorMessage);
            }, METADATA_TIMEOUT_MS);
        },
        [clearPendingTimeout],
    );

    useEffect(() => {
        function handleMetadataChanged(metadata: string) {
            const isRecording = parseRecordingFromMetadata(metadata);
            if (isRecording === null) return;
            if (!isMountedRef.current) return;

            clearPendingTimeout();

            if (isRecording) {
                stableStateRef.current = 'recording';
                setRecordingState('recording');
            } else {
                stableStateRef.current = 'idle';
                setRecordingState('idle');
            }
        }

        room.on(RoomEvent.RoomMetadataChanged, handleMetadataChanged);
        return () => {
            room.off(RoomEvent.RoomMetadataChanged, handleMetadataChanged);
        };
    }, [room, clearPendingTimeout]);

    const handleStartRecording = useCallback(async () => {
        if (!meetingId) return;

        setError(null);
        setRecordingState('starting');

        try {
            await sdkStartRecording({
                path: { id: meetingId },
                throwOnError: true,
            });
            schedulePendingTimeout(t('recordingStartError'));
        } catch {
            if (!isMountedRef.current) return;
            setRecordingState(stableStateRef.current);
            setError(t('recordingStartError'));
        }
    }, [meetingId, schedulePendingTimeout, t]);

    const handleStopRecording = useCallback(async () => {
        if (!meetingId) return;

        setError(null);
        setRecordingState('stopping');

        try {
            await sdkStopRecording({
                path: { id: meetingId },
                throwOnError: true,
            });
            schedulePendingTimeout(t('recordingStopError'));
        } catch {
            if (!isMountedRef.current) return;
            setRecordingState(stableStateRef.current);
            setError(t('recordingStopError'));
        }
    }, [meetingId, schedulePendingTimeout, t]);

    const clearError = useCallback(() => {
        setError(null);
    }, []);

    return {
        recordingState,
        error,
        startRecording: handleStartRecording,
        stopRecording: handleStopRecording,
        clearError,
    };
}
