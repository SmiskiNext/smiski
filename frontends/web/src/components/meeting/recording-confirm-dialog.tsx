'use client';

import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import type { RecordingState } from '@/hooks/use-recording-state.ts';

type RecordingConfirmDialogProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    recordingState: RecordingState;
    error: string | null;
    onConfirm: () => Promise<void>;
};

/**
 * Confirmation dialog that gates the recording start action behind an explicit
 * host prompt. Shows a loading spinner during the API call, and an inline error
 * banner with a Retry label if the call fails.
 */
export function RecordingConfirmDialog({
    open,
    onOpenChange,
    recordingState,
    error,
    onConfirm,
}: RecordingConfirmDialogProps) {
    const t = useTranslations('meetingRoom');

    const isStarting = recordingState === 'starting';
    const hasAttempted = Boolean(error);

    function handleOpenChange(nextOpen: boolean) {
        if (isStarting) return;
        onOpenChange(nextOpen);
    }

    return (
        <Dialog onOpenChange={handleOpenChange} open={open}>
            <DialogContent className='max-w-sm'>
                <DialogHeader>
                    <DialogTitle>{t('recordingConfirmTitle')}</DialogTitle>
                </DialogHeader>

                <DialogDescription className='text-text-secondary'>
                    {t('recordingConfirmMessage')}
                </DialogDescription>

                {error && (
                    <div className='rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                        <p className='text-sm text-error-dark'>{error}</p>
                    </div>
                )}

                <DialogFooter>
                    <Button
                        disabled={isStarting}
                        onClick={() => onOpenChange(false)}
                        type='button'
                        variant='outline'
                    >
                        {t('recordingConfirmCancel')}
                    </Button>
                    <Button
                        className='bg-primary text-white hover:bg-primary/90'
                        disabled={isStarting}
                        onClick={() => void onConfirm()}
                        type='button'
                        variant='default'
                    >
                        {isStarting && (
                            <Loader2 className='mr-2 h-4 w-4 animate-spin' />
                        )}
                        {isStarting
                            ? t('recordingStarting')
                            : hasAttempted
                              ? t('recordingRetry')
                              : t('recordingConfirmStart')}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
