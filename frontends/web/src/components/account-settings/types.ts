'use client';

import type { UserManagementUserResponse } from '@/generated/types.gen.ts';

export type AccountSettingsPhase = 'LOADING' | 'SUCCESS' | 'ERROR';

export type AccountSettingsState = {
    phase: AccountSettingsPhase;
    profile: UserManagementUserResponse | null;
    errorMessage: string | null;
};

export type SavePhase = 'IDLE' | 'SAVING' | 'SUCCESS' | 'ERROR';

export type LogoutPhase = 'IDLE' | 'LOGGING_OUT' | 'ERROR';

export type DeletePhase = 'IDLE' | 'CONFIRMING' | 'DELETING' | 'ERROR';

export type AccountSettingsDialogState = {
    deletePhase: DeletePhase;
    deleteErrorMessage: string | null;
};

export type AccountSettingsResult = {
    state: AccountSettingsState;
    saveState: SavePhase;
    saveErrorMessage: string | null;
    logoutState: LogoutPhase;
    logoutErrorMessage: string | null;
    dialogState: AccountSettingsDialogState;
    isMutating: boolean;
    actions: {
        retry: () => void;
        save: (
            payload: {
                fullName: string;
                username: string;
                avatarUrl?: string;
            },
            errorFallback: string,
        ) => Promise<void>;
        logout: (locale: string, errorFallback: string) => Promise<void>;
        openDeleteDialog: () => void;
        closeDeleteDialog: () => void;
        confirmDelete: (locale: string, errorFallback: string) => Promise<void>;
    };
};
