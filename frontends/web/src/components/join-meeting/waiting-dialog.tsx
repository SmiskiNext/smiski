'use client';

import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';

type WaitingDialogProps = {
    open: boolean;
    meetingTitle: string;
    onCancel: () => void;
};

export function WaitingDialog({
    open,
    meetingTitle,
    onCancel,
}: WaitingDialogProps) {
    const t = useTranslations('joinMeeting');

    return (
        <Dialog open={open}>
            <DialogContent className='max-w-sm text-center'>
                <DialogHeader className='items-center'>
                    <div className='mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-primary/10'>
                        <Loader2 className='h-7 w-7 animate-spin text-primary' />
                    </div>
                    <DialogTitle>{t('waitingTitle')}</DialogTitle>
                    <DialogDescription>
                        {t('waitingDescription', { meetingTitle })}
                    </DialogDescription>
                </DialogHeader>
                <Button
                    className='mt-2 w-full'
                    onClick={onCancel}
                    type='button'
                    variant='outline'
                >
                    {t('cancelWaiting')}
                </Button>
            </DialogContent>
        </Dialog>
    );
}
