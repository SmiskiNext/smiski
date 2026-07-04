'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Check, Copy, Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { MeetingSettingsForm } from '@/components/create-meeting/meeting-settings-form.tsx';
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from '@/components/ui/alert-dialog.tsx';
import { Badge } from '@/components/ui/badge.tsx';
import { Button } from '@/components/ui/button.tsx';
import { Form } from '@/components/ui/form.tsx';
import { Separator } from '@/components/ui/separator.tsx';
import {
    Sheet,
    SheetContent,
    SheetHeader,
    SheetTitle,
} from '@/components/ui/sheet.tsx';
import {
    Tabs,
    TabsContent,
    TabsList,
    TabsTrigger,
} from '@/components/ui/tabs.tsx';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import { getMeeting, putMeetingSettings } from '@/generated/sdk.gen.ts';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import {
    MEETING_SETTINGS_DEFAULTS,
    mapResponseToSettings,
    mapSettingsToRequest,
    meetingSettingsSchema,
} from '@/lib/schemas/meeting.ts';
import { InviteManagementSection } from './invite-management-section.tsx';
import type { DetailSheetTab } from './types.ts';
import { useInviteManagement } from './use-invite-management.ts';

const editSettingsSchema = z.object({
    settings: meetingSettingsSchema,
});

type EditSettingsValues = z.infer<typeof editSettingsSchema>;

type MeetingDetailSheetProps = {
    meeting: MeetingManagementMeetingResponse | null;
    open: boolean;
    copiedShortCode: string | null;
    initialTab?: DetailSheetTab;
    onClose: () => void;
    onCopyLink: (shortCode: string) => Promise<void>;
    onCancel: (meeting: MeetingManagementMeetingResponse) => void;
    onEnd: (meeting: MeetingManagementMeetingResponse) => void;
};

function formatFullDateTimeRange(
    startTime: string | undefined,
    endTime: string | undefined,
): string {
    if (!startTime) return '';
    const start = new Date(startTime);
    const dateStr = start.toLocaleDateString(undefined, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
    });
    const startTimeStr = start.toLocaleTimeString(undefined, {
        hour: 'numeric',
        minute: '2-digit',
    });
    if (!endTime) return `${dateStr} · ${startTimeStr}`;
    const end = new Date(endTime);
    const endTimeStr = end.toLocaleTimeString(undefined, {
        hour: 'numeric',
        minute: '2-digit',
    });
    return `${dateStr} · ${startTimeStr} – ${endTimeStr}`;
}

function StatusBadge({
    status,
    liveLabel,
    scheduledLabel,
}: {
    status: string | undefined;
    liveLabel: string;
    scheduledLabel: string;
}) {
    if (!status) return null;
    if (status === 'LIVE') {
        return (
            <Badge className='border-transparent bg-success-subtle text-success'>
                {liveLabel}
            </Badge>
        );
    }
    if (status === 'SCHEDULED') {
        return <Badge variant='secondary'>{scheduledLabel}</Badge>;
    }
    return <Badge variant='secondary'>{status}</Badge>;
}

/**
 * Right-side sheet that surfaces full upcoming meeting details and exposes
 * inline-editable settings via tabs. Replaces the prior detail dialog and
 * settings dialog stacking by consolidating overview, invitees and settings
 * into a single panel with view/edit toggle and dirty-state guarding.
 */
export function MeetingDetailSheet({
    meeting,
    open,
    copiedShortCode,
    initialTab = 'overview',
    onClose,
    onCopyLink,
    onCancel,
    onEnd,
}: MeetingDetailSheetProps) {
    const t = useTranslations('workspace.home');
    const tSheet = useTranslations('workspace.home.detailSheet');
    const locale = useLocale();
    const router = useRouter();

    const [activeTab, setActiveTab] = useState<DetailSheetTab>(initialTab);
    const [editing, setEditing] = useState(false);
    const [isLoadingSettings, setIsLoadingSettings] = useState(false);
    const [isSavingSettings, setIsSavingSettings] = useState(false);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [pendingClose, setPendingClose] = useState(false);

    const form = useForm<EditSettingsValues>({
        resolver: zodResolver(editSettingsSchema),
        defaultValues: { settings: MEETING_SETTINGS_DEFAULTS },
    });
    const isDirty = form.formState.isDirty;

    const inviteManagement = useInviteManagement(meeting?.id ?? '');
    const inviteeCount =
        inviteManagement.listState.phase === 'SUCCESS'
            ? inviteManagement.listState.invitees.length
            : 0;

    const loadSettings = useCallback(
        (id: string) => {
            setLoadError(null);
            setIsLoadingSettings(true);

            getMeeting({ path: { id } })
                .then(({ data }) => {
                    const settings = data?.settings;
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
                        setLoadError(tSheet('loadError'));
                    }
                })
                .finally(() => {
                    setIsLoadingSettings(false);
                });
        },
        [form, tSheet],
    );

    useEffect(() => {
        if (!open || !meeting?.id) return;
        setActiveTab(initialTab);
        setEditing(false);
        setSaveError(null);
        loadSettings(meeting.id);
    }, [open, meeting?.id, initialTab, loadSettings]);

    function handlePrimaryAction() {
        if (!meeting?.shortCode) return;
        router.push(
            `/${locale}/workspace/green-room?code=${meeting.shortCode}`,
        );
    }

    function handleCopyLink() {
        if (meeting?.shortCode) {
            void onCopyLink(meeting.shortCode);
        }
    }

    function handleCancel() {
        if (meeting) onCancel(meeting);
    }

    function handleEnd() {
        if (meeting) onEnd(meeting);
    }

    function attemptClose() {
        if (isDirty) {
            setPendingClose(true);
            return;
        }
        onClose();
    }

    function handleSheetOpenChange(nextOpen: boolean) {
        if (nextOpen) return;
        attemptClose();
    }

    function handleConfirmDiscard() {
        form.reset();
        setEditing(false);
        setPendingClose(false);
        onClose();
    }

    function handleResetSettings() {
        form.reset();
    }

    async function handleSaveSettings(values: EditSettingsValues) {
        if (!meeting?.id) return;

        setSaveError(null);
        setIsSavingSettings(true);

        try {
            await putMeetingSettings({
                path: { id: meeting.id },
                body: mapSettingsToRequest(values.settings),
                throwOnError: true,
            });
            form.reset(values);
            setEditing(false);
        } catch (error) {
            if (
                error instanceof ApiFailError
                && error.code === 'MEETING_FULL'
            ) {
                setSaveError(tSheet('saveErrorMaxBelowActive'));
            } else if (
                error instanceof ApiFailError
                || error instanceof ApiError
            ) {
                setSaveError(error.message);
            } else {
                setSaveError(tSheet('saveError'));
            }
        } finally {
            setIsSavingSettings(false);
        }
    }

    if (!meeting) return null;

    const title = meeting.title || t('untitledMeeting');
    const dateTimeRange = formatFullDateTimeRange(
        meeting.startTime,
        meeting.endTime,
    );
    const primaryActionLabel =
        meeting.status === 'LIVE' ? t('joinMeeting') : t('startMeeting');
    const isCopied =
        copiedShortCode != null && copiedShortCode === meeting.shortCode;
    const showDangerZone =
        meeting.status === 'SCHEDULED' || meeting.status === 'LIVE';

    return (
        <>
            <Sheet onOpenChange={handleSheetOpenChange} open={open}>
                <SheetContent
                    className='flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-xl'
                    side='right'
                >
                    <TooltipProvider delayDuration={150}>
                        <SheetHeader className='gap-3 border-b border-border-muted px-6 pb-5 pt-6'>
                            <div className='flex flex-wrap items-center gap-2 pr-8'>
                                <SheetTitle className='text-xl font-semibold leading-tight tracking-tight text-text-dark'>
                                    {title}
                                </SheetTitle>
                                <StatusBadge
                                    liveLabel={t('statusLive')}
                                    scheduledLabel={t('statusScheduled')}
                                    status={meeting.status}
                                />
                                {meeting.type && (
                                    <Badge variant='outline'>
                                        {meeting.type}
                                    </Badge>
                                )}
                            </div>
                            {dateTimeRange && (
                                <p className='text-sm text-text-muted'>
                                    {dateTimeRange}
                                </p>
                            )}
                            {meeting.shortCode && (
                                <div className='flex items-center gap-2'>
                                    <span className='text-xs uppercase tracking-wide text-text-subtle'>
                                        {t('meetingShortCode')}
                                    </span>
                                    <code className='rounded-md bg-surface-input px-2 py-0.5 font-mono text-sm text-text-dark'>
                                        {meeting.shortCode}
                                    </code>
                                    <Tooltip>
                                        <TooltipTrigger asChild>
                                            <Button
                                                aria-label={
                                                    isCopied
                                                        ? t('codeCopied')
                                                        : t('copyCode')
                                                }
                                                onClick={handleCopyLink}
                                                size='icon'
                                                type='button'
                                                variant='ghost'
                                            >
                                                {isCopied ? (
                                                    <Check className='text-success' />
                                                ) : (
                                                    <Copy />
                                                )}
                                            </Button>
                                        </TooltipTrigger>
                                        <TooltipContent>
                                            {isCopied
                                                ? t('codeCopied')
                                                : t('copyCode')}
                                        </TooltipContent>
                                    </Tooltip>
                                </div>
                            )}
                            {meeting.shortCode && (
                                <div className='flex flex-wrap gap-2 pt-1'>
                                    <Button
                                        onClick={handlePrimaryAction}
                                        type='button'
                                    >
                                        {primaryActionLabel}
                                    </Button>
                                </div>
                            )}
                        </SheetHeader>

                        <div className='flex-1 px-6 py-5'>
                            <Tabs
                                onValueChange={(value) =>
                                    setActiveTab(value as DetailSheetTab)
                                }
                                value={activeTab}
                            >
                                <TabsList className='grid w-full grid-cols-3'>
                                    <TabsTrigger value='overview'>
                                        {tSheet('tabOverview')}
                                    </TabsTrigger>
                                    <TabsTrigger value='invitees'>
                                        {inviteeCount > 0
                                            ? tSheet('tabInviteesWithCount', {
                                                  count: inviteeCount,
                                              })
                                            : tSheet('tabInvitees')}
                                    </TabsTrigger>
                                    <TabsTrigger value='settings'>
                                        {tSheet('tabSettings')}
                                    </TabsTrigger>
                                </TabsList>

                                <TabsContent
                                    className='mt-5 space-y-3'
                                    value='overview'
                                >
                                    <p className='text-xs font-medium uppercase tracking-wide text-text-subtle'>
                                        {t('meetingDescription')}
                                    </p>
                                    <p className='text-sm leading-relaxed text-text-secondary'>
                                        {meeting.description
                                            || tSheet('descriptionEmpty')}
                                    </p>
                                </TabsContent>

                                <TabsContent className='mt-5' value='invitees'>
                                    {meeting.id && (
                                        <InviteManagementSection
                                            addState={inviteManagement.addState}
                                            listState={
                                                inviteManagement.listState
                                            }
                                            onAddInvitee={
                                                inviteManagement.handleAddInvitee
                                            }
                                            onResend={
                                                inviteManagement.handleResend
                                            }
                                            onRevoke={
                                                inviteManagement.handleRevoke
                                            }
                                            rowStates={
                                                inviteManagement.rowStates
                                            }
                                        />
                                    )}
                                </TabsContent>

                                <TabsContent
                                    className='mt-5 space-y-4'
                                    value='settings'
                                >
                                    {isLoadingSettings && (
                                        <div className='flex items-center gap-2 py-6 text-sm text-text-secondary'>
                                            <Loader2 className='h-4 w-4 animate-spin text-primary' />
                                            <span>{tSheet('loading')}</span>
                                        </div>
                                    )}

                                    {loadError && !isLoadingSettings && (
                                        <div className='rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                                            <p className='text-sm text-error-dark'>
                                                {loadError}
                                            </p>
                                            <Button
                                                className='mt-2'
                                                onClick={() =>
                                                    meeting.id
                                                    && loadSettings(meeting.id)
                                                }
                                                size='sm'
                                                type='button'
                                                variant='outline'
                                            >
                                                {tSheet('retry')}
                                            </Button>
                                        </div>
                                    )}

                                    {!isLoadingSettings && !loadError && (
                                        <Form {...form}>
                                            <form
                                                className='space-y-4'
                                                onSubmit={form.handleSubmit(
                                                    handleSaveSettings,
                                                )}
                                            >
                                                <div className='flex items-center justify-between gap-3'>
                                                    <p className='text-xs text-text-subtle'>
                                                        {!editing
                                                            && tSheet(
                                                                'viewModeHint',
                                                            )}
                                                    </p>
                                                    {!editing && (
                                                        <Button
                                                            onClick={() =>
                                                                setEditing(true)
                                                            }
                                                            size='sm'
                                                            type='button'
                                                            variant='outline'
                                                        >
                                                            {tSheet(
                                                                'editButton',
                                                            )}
                                                        </Button>
                                                    )}
                                                </div>

                                                <MeetingSettingsForm
                                                    disabled={!editing}
                                                    form={form}
                                                    layout='grid'
                                                />

                                                {saveError && (
                                                    <div className='rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                                                        <p className='text-sm text-error-dark'>
                                                            {saveError}
                                                        </p>
                                                    </div>
                                                )}

                                                {isDirty && (
                                                    <div className='sticky bottom-0 -mx-6 flex items-center justify-end gap-2 border-t border-border-muted bg-surface px-6 py-3 shadow-[0_-12px_24px_-24px_rgba(15,23,42,0.4)]'>
                                                        <Button
                                                            disabled={
                                                                isSavingSettings
                                                            }
                                                            onClick={
                                                                handleResetSettings
                                                            }
                                                            type='button'
                                                            variant='ghost'
                                                        >
                                                            {tSheet(
                                                                'resetChanges',
                                                            )}
                                                        </Button>
                                                        <Button
                                                            disabled={
                                                                isSavingSettings
                                                            }
                                                            type='submit'
                                                        >
                                                            {isSavingSettings && (
                                                                <Loader2 className='animate-spin' />
                                                            )}
                                                            {isSavingSettings
                                                                ? tSheet(
                                                                      'saving',
                                                                  )
                                                                : tSheet(
                                                                      'saveChanges',
                                                                  )}
                                                        </Button>
                                                    </div>
                                                )}
                                            </form>
                                        </Form>
                                    )}
                                </TabsContent>
                            </Tabs>
                        </div>

                        {showDangerZone && (
                            <div className='border-t border-border-muted bg-surface-input/40 px-6 py-5'>
                                <p className='text-xs font-medium uppercase tracking-wide text-error'>
                                    {tSheet('dangerZoneTitle')}
                                </p>
                                <p className='mt-1 text-sm text-text-muted'>
                                    {meeting.status === 'LIVE'
                                        ? tSheet('dangerZoneEndDescription')
                                        : tSheet('dangerZoneDescription')}
                                </p>
                                <Separator className='my-3' />
                                {meeting.status === 'SCHEDULED' && (
                                    <Button
                                        onClick={handleCancel}
                                        type='button'
                                        variant='destructive'
                                    >
                                        {t('cancelMeeting')}
                                    </Button>
                                )}
                                {meeting.status === 'LIVE' && (
                                    <Button
                                        onClick={handleEnd}
                                        type='button'
                                        variant='destructive'
                                    >
                                        {t('endMeeting')}
                                    </Button>
                                )}
                            </div>
                        )}
                    </TooltipProvider>
                </SheetContent>
            </Sheet>

            <AlertDialog
                onOpenChange={(nextOpen) => {
                    if (!nextOpen) setPendingClose(false);
                }}
                open={pendingClose}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>
                            {tSheet('unsavedChangesTitle')}
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            {tSheet('unsavedChangesDescription')}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel
                            onClick={() => setPendingClose(false)}
                        >
                            {tSheet('keepEditing')}
                        </AlertDialogCancel>
                        <AlertDialogAction onClick={handleConfirmDiscard}>
                            {tSheet('discardChanges')}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
}
