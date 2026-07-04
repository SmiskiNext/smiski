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
import type { ConfirmEndMessages } from './types.ts';

type EndMeetingDialogProps = {
    meeting: MeetingManagementMeetingResponse | null;
    open: boolean;
    isEnding: boolean;
    endError: string | null;
    onClose: () => void;
    onConfirm: (messages: ConfirmEndMessages) => Promise<void>;
};

/** Confirmation dialog for ending a live host meeting. */
export function EndMeetingDialog({
    meeting,
    open,
    isEnding,
    endError,
    onClose,
    onConfirm,
}: EndMeetingDialogProps) {
    const t = useTranslations('workspace.home');

    const title = meeting?.title || t('untitledMeeting');

    function handleOpenChange(isOpen: boolean) {
        if (!isOpen && !isEnding) {
            onClose();
        }
    }

    function handleConfirm() {
        void onConfirm({
            errorFallback: t('endError'),
        });
    }

    return (
        <Dialog onOpenChange={handleOpenChange} open={open}>
            <DialogContent className='max-w-md'>
                <DialogHeader>
                    <DialogTitle>{t('endConfirmTitle')}</DialogTitle>
                </DialogHeader>

                <p className='text-sm text-text-muted'>
                    {t('endConfirmMessage', { title })}
                </p>

                {endError && (
                    <div className='rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                        <p className='text-sm text-error-dark'>{endError}</p>
                    </div>
                )}

                <DialogFooter>
                    <Button
                        disabled={isEnding}
                        onClick={onClose}
                        type='button'
                        variant='outline'
                    >
                        {t('endDismissAction')}
                    </Button>
                    <Button
                        disabled={isEnding}
                        onClick={handleConfirm}
                        type='button'
                        variant='destructive'
                    >
                        {isEnding && (
                            <Loader2 className='h-4 w-4 animate-spin' />
                        )}
                        {t('endConfirmAction')}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
