'use client';

import { useCallback, useEffect, useState } from 'react';
import {
    addInvitee,
    getInvitees,
    resendInvite,
    revokeInvite,
} from '@/generated/sdk.gen.ts';
import type { MeetingManagementInviteeListResponse } from '@/generated/types.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';

type InviteListState =
    | { phase: 'LOADING' }
    | { phase: 'SUCCESS'; invitees: MeetingManagementInviteeListResponse[] }
    | { phase: 'ERROR'; message: string };

type PerRowActionState = {
    isLoading: boolean;
    error: string | null;
};

export type UseInviteManagementReturn = {
    listState: InviteListState;
    addState: { isLoading: boolean; error: string | null };
    rowStates: Record<string, PerRowActionState>;
    handleAddInvitee: (email: string) => Promise<boolean>;
    handleResend: (inviteeId: string) => Promise<void>;
    handleRevoke: (inviteeId: string) => Promise<void>;
};

/** Extracts a display message from API errors or falls back to a default. */
function resolveErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof ApiFailError || error instanceof ApiError) {
        return error.message;
    }
    return fallback;
}

/** Manages all invite CRUD state for a single meeting's invitee list. */
export function useInviteManagement(
    meetingId: string,
): UseInviteManagementReturn {
    const [listState, setListState] = useState<InviteListState>({
        phase: 'LOADING',
    });
    const [addState, setAddState] = useState<{
        isLoading: boolean;
        error: string | null;
    }>({ isLoading: false, error: null });
    const [rowStates, setRowStates] = useState<
        Record<string, PerRowActionState>
    >({});

    const loadInvitees = useCallback(() => {
        if (!meetingId) return;

        setListState({ phase: 'LOADING' });

        getInvitees({ path: { meetingId } })
            .then(({ data }) => {
                setListState({
                    phase: 'SUCCESS',
                    invitees: data ?? [],
                });
            })
            .catch(() => {
                setListState({
                    phase: 'ERROR',
                    message: 'Failed to load invitees.',
                });
            });
    }, [meetingId]);

    useEffect(() => {
        loadInvitees();
    }, [loadInvitees]);

    const handleAddInvitee = useCallback(
        async (email: string): Promise<boolean> => {
            setAddState({ isLoading: true, error: null });

            try {
                await addInvitee({
                    path: { meetingId },
                    body: { email },
                    throwOnError: true,
                });
                setAddState({ isLoading: false, error: null });
                loadInvitees();
                return true;
            } catch (error) {
                setAddState({
                    isLoading: false,
                    error: resolveErrorMessage(
                        error,
                        'Failed to add invitee. Please try again.',
                    ),
                });
                return false;
            }
        },
        [meetingId, loadInvitees],
    );

    const handleResend = useCallback(
        async (inviteeId: string) => {
            setRowStates((prev) => ({
                ...prev,
                [inviteeId]: { isLoading: true, error: null },
            }));

            try {
                await resendInvite({
                    path: { meetingId, inviteeId },
                    throwOnError: true,
                });
                setRowStates((prev) => ({
                    ...prev,
                    [inviteeId]: { isLoading: false, error: null },
                }));
                loadInvitees();
            } catch (error) {
                setRowStates((prev) => ({
                    ...prev,
                    [inviteeId]: {
                        isLoading: false,
                        error: resolveErrorMessage(
                            error,
                            'Failed to resend invite. Please try again.',
                        ),
                    },
                }));
            }
        },
        [meetingId, loadInvitees],
    );

    const handleRevoke = useCallback(
        async (inviteeId: string) => {
            setRowStates((prev) => ({
                ...prev,
                [inviteeId]: { isLoading: true, error: null },
            }));

            try {
                await revokeInvite({
                    path: { meetingId, inviteeId },
                    throwOnError: true,
                });
                setRowStates((prev) => ({
                    ...prev,
                    [inviteeId]: { isLoading: false, error: null },
                }));
                loadInvitees();
            } catch (error) {
                setRowStates((prev) => ({
                    ...prev,
                    [inviteeId]: {
                        isLoading: false,
                        error: resolveErrorMessage(
                            error,
                            'Failed to revoke invite. Please try again.',
                        ),
                    },
                }));
            }
        },
        [meetingId, loadInvitees],
    );

    return {
        listState,
        addState,
        rowStates,
        handleAddInvitee,
        handleResend,
        handleRevoke,
    };
}
