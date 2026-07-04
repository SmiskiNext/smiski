'use client';

import { useRoomContext } from '@livekit/components-react';
import { Loader2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useCallback, useState } from 'react';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import { endMeeting } from '@/generated/sdk.gen.ts';

type LeaveDialogProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    isHost: boolean;
    meetingId: string | null;
};

/**
 * Confirmation dialog that disconnects the LiveKit room before navigating
 * the user back to the workspace.
 *
 * Hosts see two exit options: leave locally or end the meeting for all
 * participants. Non-hosts see the existing single-action leave confirmation.
 */
export function LeaveDialog({
    open,
    onOpenChange,
    isHost,
    meetingId,
}: LeaveDialogProps) {
    const t = useTranslations('meetingRoom');
    const locale = useLocale();
    const room = useRoomContext();

    const [isEndingForAll, setIsEndingForAll] = useState(false);
    const [endError, setEndError] = useState<string | null>(null);

    const navigateToWorkspace = useCallback(() => {
        window.location.href = `/${locale}/workspace`;
    }, [locale]);

    const handleConfirmLeave = useCallback(async () => {
        try {
            await room.disconnect();
        } finally {
            navigateToWorkspace();
        }
    }, [room, navigateToWorkspace]);

    const handleEndForAll = useCallback(async () => {
        if (!meetingId || isEndingForAll) return;

        setIsEndingForAll(true);
        setEndError(null);

        try {
            await endMeeting({ path: { id: meetingId } });
            await room.disconnect();
            navigateToWorkspace();
        } catch {
            setEndError(t('hostLeaveDialogEndError'));
            setIsEndingForAll(false);
        }
    }, [meetingId, isEndingForAll, room, navigateToWorkspace, t]);

    function handleOpenChange(nextOpen: boolean) {
        if (!nextOpen && isEndingForAll) return;
        if (!nextOpen) setEndError(null);
        onOpenChange(nextOpen);
    }

    if (isHost) {
        return (
            <Dialog onOpenChange={handleOpenChange} open={open}>
                <DialogContent className='max-w-sm'>
                    <DialogHeader>
                        <DialogTitle>{t('hostLeaveDialogTitle')}</DialogTitle>
                    </DialogHeader>

                    <DialogDescription className='text-text-secondary'>
                        {t('hostLeaveDialogMessage')}
                    </DialogDescription>

                    {endError && (
                        <div className='rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                            <p className='text-sm text-error-dark'>
                                {endError}
                            </p>
                        </div>
                    )}

                    <DialogFooter>
                        <Button
                            disabled={isEndingForAll}
                            onClick={() => onOpenChange(false)}
                            type='button'
                            variant='outline'
                        >
                            {t('leaveDialogCancel')}
                        </Button>
                        <Button
                            disabled={isEndingForAll}
                            onClick={() => void handleConfirmLeave()}
                            type='button'
                            variant='outline'
                        >
                            {t('hostLeaveDialogLeave')}
                        </Button>
                        <Button
                            disabled={isEndingForAll}
                            onClick={() => void handleEndForAll()}
                            type='button'
                            variant='destructive'
                        >
                            {isEndingForAll && (
                                <Loader2 className='h-4 w-4 animate-spin' />
                            )}
                            {isEndingForAll
                                ? t('hostLeaveDialogEndForAllLoading')
                                : t('hostLeaveDialogEndForAll')}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        );
    }

    return (
        <Dialog onOpenChange={onOpenChange} open={open}>
            <DialogContent className='max-w-sm'>
                <DialogHeader>
                    <DialogTitle>{t('leaveDialogTitle')}</DialogTitle>
                </DialogHeader>

                <DialogDescription className='text-text-secondary'>
                    {t('leaveDialogMessage')}
                </DialogDescription>

                <DialogFooter>
                    <Button
                        onClick={() => onOpenChange(false)}
                        type='button'
                        variant='outline'
                    >
                        {t('leaveDialogCancel')}
                    </Button>
                    <Button
                        onClick={() => void handleConfirmLeave()}
                        type='button'
                        variant='destructive'
                    >
                        {t('leaveDialogConfirm')}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
