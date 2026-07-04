'use client';

import { useCallback, useEffect, useState } from 'react';
import { deleteMe, getMe, putMe } from '@/generated/sdk.gen.ts';
import type { UserManagementUserResponse } from '@/generated/types.gen.ts';
import { useLogout } from '@/hooks/use-logout.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import { clearAuthCookies } from '@/lib/auth/cookies.ts';
import type {
    AccountSettingsDialogState,
    AccountSettingsResult,
    AccountSettingsState,
    SavePhase,
} from './types.ts';

export function useUserAccountSettings(): AccountSettingsResult {
    const [state, setState] = useState<AccountSettingsState>({
        phase: 'LOADING',
        profile: null,
        errorMessage: null,
    });
    const [saveState, setSaveState] = useState<SavePhase>('IDLE');
    const [saveErrorMessage, setSaveErrorMessage] = useState<string | null>(
        null,
    );
    const {
        logoutState,
        logoutErrorMessage,
        logout: logoutAction,
    } = useLogout();
    const [dialogState, setDialogState] = useState<AccountSettingsDialogState>({
        deletePhase: 'IDLE',
        deleteErrorMessage: null,
    });

    const loadProfile = useCallback(() => {
        setState({ phase: 'LOADING', profile: null, errorMessage: null });

        getMe()
            .then(({ data }) => {
                setState({
                    phase: 'SUCCESS',
                    profile: data as UserManagementUserResponse,
                    errorMessage: null,
                });
            })
            .catch(() => {
                setState({
                    phase: 'ERROR',
                    profile: null,
                    errorMessage: null,
                });
            });
    }, []);

    useEffect(() => {
        loadProfile();
    }, [loadProfile]);

    const retry = useCallback(() => {
        loadProfile();
    }, [loadProfile]);

    const save = useCallback(
        async (
            payload: {
                fullName: string;
                username: string;
                avatarUrl?: string;
            },
            errorFallback: string,
        ) => {
            setSaveState('SAVING');
            setSaveErrorMessage(null);

            try {
                const { data } = await putMe({
                    body: payload,
                    throwOnError: true,
                });
                setState({
                    phase: 'SUCCESS',
                    profile: data as UserManagementUserResponse,
                    errorMessage: null,
                });
                setSaveState('SUCCESS');
                setTimeout(() => setSaveState('IDLE'), 2500);
            } catch (err) {
                if (err instanceof ApiFailError || err instanceof ApiError) {
                    setSaveErrorMessage(err.message);
                } else {
                    setSaveErrorMessage(errorFallback);
                }
                setSaveState('ERROR');
            }
        },
        [],
    );

    const performRedirect = useCallback((locale: string) => {
        window.location.href = `/${locale}/login`;
    }, []);

    const openDeleteDialog = useCallback(() => {
        setDialogState({ deletePhase: 'CONFIRMING', deleteErrorMessage: null });
    }, []);

    const closeDeleteDialog = useCallback(() => {
        setDialogState({ deletePhase: 'IDLE', deleteErrorMessage: null });
    }, []);

    const confirmDelete = useCallback(
        async (locale: string, errorFallback: string) => {
            setDialogState((prev) => ({
                ...prev,
                deletePhase: 'DELETING',
                deleteErrorMessage: null,
            }));

            try {
                await deleteMe({ throwOnError: true });
                setDialogState({
                    deletePhase: 'IDLE',
                    deleteErrorMessage: null,
                });
                await clearAuthCookies();
                performRedirect(locale);
            } catch (err) {
                if (err instanceof ApiFailError || err instanceof ApiError) {
                    setDialogState({
                        deletePhase: 'ERROR',
                        deleteErrorMessage: err.message,
                    });
                } else {
                    setDialogState({
                        deletePhase: 'ERROR',
                        deleteErrorMessage: errorFallback,
                    });
                }
            }
        },
        [performRedirect],
    );

    const isMutating =
        saveState === 'SAVING'
        || logoutState === 'LOGGING_OUT'
        || dialogState.deletePhase === 'DELETING';

    return {
        state,
        saveState,
        saveErrorMessage,
        logoutState,
        logoutErrorMessage,
        dialogState,
        isMutating,
        actions: {
            retry,
            save,
            logout: logoutAction,
            openDeleteDialog,
            closeDeleteDialog,
            confirmDelete,
        },
    };
}
