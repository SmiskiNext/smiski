'use client';

import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import type { AccountSettingsDialogState } from './types.ts';

type DeleteAccountDialogProps = {
    dialogState: AccountSettingsDialogState;
    onClose: () => void;
    onConfirm: (errorFallback: string) => Promise<void>;
};

const DELETE_CONFIRMATION_TEXT = 'DELETE';

export function DeleteAccountDialog({
    dialogState,
    onClose,
    onConfirm,
}: DeleteAccountDialogProps) {
    const t = useTranslations('workspace.accountSettings');
    const [confirmText, setConfirmText] = useState('');

    const isOpen =
        dialogState.deletePhase === 'CONFIRMING'
        || dialogState.deletePhase === 'DELETING'
        || dialogState.deletePhase === 'ERROR';

    const isDeleting = dialogState.deletePhase === 'DELETING';
    const isConfirmEnabled =
        confirmText === DELETE_CONFIRMATION_TEXT && !isDeleting;

    const handleClose = () => {
        if (isDeleting) return;
        setConfirmText('');
        onClose();
    };

    const handleConfirm = () => {
        if (!isConfirmEnabled) return;
        void onConfirm(t('deleteError'));
    };

    return (
        <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
            <DialogContent className='max-w-md'>
                <DialogHeader>
                    <DialogTitle className='text-2xl font-semibold text-error-dark'>
                        {t('deleteAccountTitle')}
                    </DialogTitle>
                    <DialogDescription className='text-base leading-7 text-text-muted'>
                        {t('deleteAccountWarning')}
                    </DialogDescription>
                </DialogHeader>

                <div>
                    <label
                        className='mb-2 block text-sm font-medium text-text-secondary'
                        htmlFor='delete-confirm'
                    >
                        {t('deleteAccountConfirmPrompt', {
                            text: DELETE_CONFIRMATION_TEXT,
                        })}
                    </label>
                    <input
                        aria-label={t('deleteAccountConfirmInputLabel')}
                        autoComplete='off'
                        className='mt-3 w-full rounded-xl border border-border-muted bg-surface px-4 py-3 text-text-dark outline-none ring-1 ring-transparent transition focus:border-error focus:ring-2 focus:ring-error'
                        disabled={isDeleting}
                        id='delete-confirm'
                        placeholder={DELETE_CONFIRMATION_TEXT}
                        type='text'
                        value={confirmText}
                        onChange={(e) => setConfirmText(e.target.value)}
                    />
                </div>

                {dialogState.deleteErrorMessage && (
                    <div className='rounded-xl border border-error-dark bg-error-subtle px-4 py-3'>
                        <p className='text-sm text-error'>
                            {dialogState.deleteErrorMessage}
                        </p>
                    </div>
                )}

                <DialogFooter className='mt-7 gap-3 sm:mt-0 sm:justify-end'>
                    <Button
                        disabled={isDeleting}
                        onClick={handleClose}
                        type='button'
                        variant='outline'
                    >
                        {t('cancel')}
                    </Button>
                    <Button
                        disabled={!isConfirmEnabled}
                        onClick={handleConfirm}
                        type='button'
                        variant='destructive'
                    >
                        {isDeleting ? t('deleting') : t('deleteAccountAction')}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
