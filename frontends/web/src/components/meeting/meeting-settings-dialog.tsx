'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { AlertCircle, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { MeetingSettingsForm } from '@/components/create-meeting/meeting-settings-form.tsx';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import { Form } from '@/components/ui/form.tsx';
import { getMeeting, putMeetingSettings } from '@/generated/sdk.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import {
    MEETING_SETTINGS_DEFAULTS,
    mapResponseToSettings,
    mapSettingsToRequest,
    meetingSettingsSchema,
} from '@/lib/schemas/meeting.ts';

const editSettingsSchema = z.object({
    settings: meetingSettingsSchema,
});

type EditSettingsValues = z.infer<typeof editSettingsSchema>;

type MeetingSettingsDialogProps = {
    meetingId: string | null;
    open: boolean;
    onClose: () => void;
};

export function MeetingSettingsDialog({
    meetingId,
    open,
    onClose,
}: MeetingSettingsDialogProps) {
    const t = useTranslations('meetingSettingsDialog');

    const [loadError, setLoadError] = useState<string | null>(null);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [hasExistingPassword, setHasExistingPassword] = useState(false);

    const form = useForm<EditSettingsValues>({
        resolver: zodResolver(editSettingsSchema),
        defaultValues: {
            settings: MEETING_SETTINGS_DEFAULTS,
        },
    });

    const loadSettings = useCallback(
        (id: string) => {
            setLoadError(null);
            setIsLoading(true);

            getMeeting({ path: { id } })
                .then(({ data }) => {
                    const settings = data?.settings;
                    setHasExistingPassword(settings?.requirePassword ?? false);
                    form.reset({
                        settings: settings
                            ? mapResponseToSettings(settings)
                            : MEETING_SETTINGS_DEFAULTS,
                    });
                })
                .catch((error) => {
                    if (
                        error instanceof ApiFailError
                        || error instanceof ApiError
                    ) {
                        setLoadError(error.message);
                    } else {
                        setLoadError(t('loadError'));
                    }
                })
                .finally(() => {
                    setIsLoading(false);
                });
        },
        [form, t],
    );

    useEffect(() => {
        if (!open || !meetingId) return;
        setSaveError(null);
        loadSettings(meetingId);
    }, [open, meetingId, loadSettings]);

    async function handleSubmit(values: EditSettingsValues) {
        if (!meetingId) return;

        setSaveError(null);
        setIsSaving(true);

        try {
            await putMeetingSettings({
                path: { id: meetingId },
                body: mapSettingsToRequest(values.settings),
                throwOnError: true,
            });
            toast.success(t('saveSuccess'));
            onClose();
        } catch (error) {
            if (
                error instanceof ApiFailError
                && error.code === 'MEETING_FULL'
            ) {
                setSaveError(t('saveErrorMaxBelowActive'));
            } else if (
                error instanceof ApiFailError
                || error instanceof ApiError
            ) {
                setSaveError(error.message);
            } else {
                setSaveError(t('saveError'));
            }
        } finally {
            setIsSaving(false);
        }
    }

    function handleOpenChange(isOpen: boolean) {
        if (!isOpen) {
            onClose();
        }
    }

    return (
        <Dialog onOpenChange={handleOpenChange} open={open}>
            <DialogContent className='max-w-lg'>
                <DialogHeader>
                    <DialogTitle>{t('title')}</DialogTitle>
                </DialogHeader>

                {isLoading && (
                    <div className='space-y-3 py-2 motion-safe:animate-pulse'>
                        <div className='h-16 rounded-xl bg-surface-input' />
                        <div className='h-16 rounded-xl bg-surface-input' />
                        <div className='h-16 rounded-xl bg-surface-input' />
                    </div>
                )}

                {loadError && !isLoading && (
                    <div
                        aria-live='polite'
                        className='flex items-start gap-3 rounded-xl border border-error/40 bg-error-subtle px-4 py-3'
                        role='alert'
                    >
                        <AlertCircle className='h-5 w-5 shrink-0 text-error-dark' />
                        <div>
                            <p className='text-sm text-error-dark'>
                                {loadError}
                            </p>
                            <Button
                                className='mt-2'
                                onClick={() =>
                                    meetingId && loadSettings(meetingId)
                                }
                                size='sm'
                                type='button'
                                variant='outline'
                            >
                                {t('retry')}
                            </Button>
                        </div>
                    </div>
                )}

                {!isLoading && !loadError && (
                    <Form {...form}>
                        <form
                            className='flex max-h-[70vh] flex-col gap-5'
                            onSubmit={form.handleSubmit(handleSubmit)}
                        >
                            <div className='flex-1 overflow-y-auto pr-1'>
                                <MeetingSettingsForm
                                    form={form}
                                    layout='split'
                                />

                                {hasExistingPassword && (
                                    <p className='mt-5 text-xs text-text-subtle'>
                                        {t('passwordHint')}
                                    </p>
                                )}

                                {saveError && (
                                    <div
                                        aria-live='assertive'
                                        className='mt-5 flex items-start gap-3 rounded-xl border border-error/40 bg-error-subtle px-4 py-3'
                                        role='alert'
                                    >
                                        <AlertCircle className='h-5 w-5 shrink-0 text-error-dark' />
                                        <div>
                                            <p className='text-sm text-error-dark'>
                                                {saveError}
                                            </p>
                                        </div>
                                    </div>
                                )}
                            </div>

                            <DialogFooter>
                                <Button
                                    disabled={isSaving}
                                    onClick={onClose}
                                    type='button'
                                    variant='outline'
                                >
                                    {t('cancel')}
                                </Button>
                                <Button
                                    disabled={
                                        !form.formState.isDirty || isSaving
                                    }
                                    type='submit'
                                >
                                    {isSaving && (
                                        <Loader2 className='h-4 w-4 animate-spin' />
                                    )}
                                    {isSaving ? t('saving') : t('save')}
                                </Button>
                            </DialogFooter>
                        </form>
                    </Form>
                )}
            </DialogContent>
        </Dialog>
    );
}
