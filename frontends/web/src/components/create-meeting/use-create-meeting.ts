'use client';

import { useCallback, useReducer } from 'react';
import { createInstantMeeting } from '@/generated/sdk.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import type { InstantMeetingValues } from '@/lib/schemas/meeting.ts';
import { mapSettingsToRequest } from '@/lib/schemas/meeting.ts';

export type CreateMeetingState =
    | { phase: 'IDLE' }
    | { phase: 'CREATING' }
    | { phase: 'READY'; meetingId: string; shortCode: string }
    | { phase: 'ERROR'; message: string; retryable: boolean };

type CreateMeetingAction =
    | { type: 'CREATE_STARTED' }
    | { type: 'CREATE_SUCCEEDED'; meetingId: string; shortCode: string }
    | { type: 'FAILED'; message: string; retryable: boolean }
    | { type: 'RETRY' }
    | { type: 'RESET' };

const INITIAL_STATE: CreateMeetingState = { phase: 'IDLE' };

export function createMeetingReducer(
    state: CreateMeetingState,
    action: CreateMeetingAction,
): CreateMeetingState {
    switch (action.type) {
        case 'CREATE_STARTED':
            return { phase: 'CREATING' };

        case 'CREATE_SUCCEEDED':
            return {
                phase: 'READY',
                meetingId: action.meetingId,
                shortCode: action.shortCode,
            };

        case 'FAILED':
            return {
                phase: 'ERROR',
                message: action.message,
                retryable: action.retryable,
            };

        case 'RETRY':
        case 'RESET':
            return INITIAL_STATE;

        default:
            return state;
    }
}

export type UseCreateMeetingReturn = {
    state: CreateMeetingState;
    create: (values: InstantMeetingValues) => Promise<void>;
    retry: () => void;
    reset: () => void;
};

export function useCreateMeeting(): UseCreateMeetingReturn {
    const [state, dispatch] = useReducer(createMeetingReducer, INITIAL_STATE);

    const create = useCallback(async (values: InstantMeetingValues) => {
        dispatch({ type: 'CREATE_STARTED' });

        try {
            const { data: meeting } = await createInstantMeeting({
                body: {
                    title: values.title || undefined,
                    settings: mapSettingsToRequest(values.settings),
                },
                throwOnError: true,
            });

            const meetingId = meeting?.id;
            const shortCode = meeting?.shortCode;
            if (!meetingId || !shortCode) {
                dispatch({
                    type: 'FAILED',
                    message: 'Meeting was created but returned no identifier.',
                    retryable: false,
                });
                return;
            }

            dispatch({ type: 'CREATE_SUCCEEDED', meetingId, shortCode });
        } catch (error) {
            if (error instanceof ApiFailError) {
                dispatch({
                    type: 'FAILED',
                    message: error.message,
                    retryable: false,
                });
            } else if (error instanceof ApiError) {
                dispatch({
                    type: 'FAILED',
                    message: error.message,
                    retryable: true,
                });
            } else {
                dispatch({
                    type: 'FAILED',
                    message: 'Network error. Please check your connection.',
                    retryable: true,
                });
            }
        }
    }, []);

    const retry = useCallback(() => {
        dispatch({ type: 'RETRY' });
    }, []);

    const reset = useCallback(() => {
        dispatch({ type: 'RESET' });
    }, []);

    return { state, create, retry, reset };
}
