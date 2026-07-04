'use client';

import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import type { ConfirmCancelMessages } from './types.ts';

type CancelMeetingDialogProps = {
    meeting: MeetingManagementMeetingResponse | null;
    open: boolean;
    isCancelling: boolean;
    cancelError: string | null;
    onClose: () => void;
    onConfirm: (messages: ConfirmCancelMessages) => Promise<void>;
};

/**
 * Confirmation dialog for cancelling an upcoming host meeting.
 * The cancel API is only called after the host explicitly confirms.
 */
export function CancelMeetingDialog({
    meeting,
    open,
    isCancelling,
    cancelError,
    onClose,
    onConfirm,
}: CancelMeetingDialogProps) {
    const t = useTranslations('workspace.home');

    const title = meeting?.title || t('untitledMeeting');

    function handleOpenChange(isOpen: boolean) {
        if (!isOpen && !isCancelling) {
            onClose();
        }
    }

    function handleConfirm() {
        void onConfirm({
            errorFallback: t('cancelError'),
            statusConflict: t('cancelStatusConflict'),
        });
    }

    return (
        <Dialog onOpenChange={handleOpenChange} open={open}>
            <DialogContent className='max-w-md'>
                <DialogHeader>
                    <DialogTitle>{t('cancelConfirmTitle')}</DialogTitle>
                </DialogHeader>

                <p className='text-sm text-text-muted'>
                    {t('cancelConfirmMessage', { title })}
                </p>

                {cancelError && (
                    <div className='rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                        <p className='text-sm text-error-dark'>{cancelError}</p>
                    </div>
                )}

                <DialogFooter>
                    <Button
                        disabled={isCancelling}
                        onClick={onClose}
                        type='button'
                        variant='outline'
                    >
                        {t('cancelDismissAction')}
                    </Button>
                    <Button
                        disabled={isCancelling}
                        onClick={handleConfirm}
                        type='button'
                        variant='destructive'
                    >
                        {isCancelling && (
                            <Loader2 className='h-4 w-4 animate-spin' />
                        )}
                        {t('cancelConfirmAction')}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
