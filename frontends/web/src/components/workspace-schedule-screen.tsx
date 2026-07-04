'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Calendar, Clock, Loader2, Users } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { InviteeInput } from '@/components/create-meeting/invitee-input.tsx';
import { MeetingSettingsForm } from '@/components/create-meeting/meeting-settings-form.tsx';
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import { Form } from '@/components/ui/form.tsx';
import { WorkspaceShell } from '@/components/workspace-shell.tsx';
import { scheduleMeeting } from '@/generated/sdk.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import {
    MEETING_SETTINGS_DEFAULTS,
    mapSettingsToRequest,
    type ScheduleMeetingValues,
    scheduleMeetingSchema,
} from '@/lib/schemas/meeting.ts';

const DURATION_OPTIONS = [15, 30, 45, 60, 90, 120];
const DESCRIPTION_MAX_LENGTH = 2000;

type ScheduleSuccessState = {
    shortCode: string;
    title: string;
    startTime: string;
};

export function WorkspaceScheduleScreen() {
    const t = useTranslations('workspace.schedule');
    const [successState, setSuccessState] =
        useState<ScheduleSuccessState | null>(null);
    const [serverError, setServerError] = useState<string | null>(null);

    const form = useForm<ScheduleMeetingValues>({
        resolver: zodResolver(scheduleMeetingSchema),
        defaultValues: {
            title: '',
            description: '',
            date: '',
            time: '',
            durationMinutes: 60,
            invitees: [],
            settings: MEETING_SETTINGS_DEFAULTS,
        },
    });

    const titleValue = form.watch('title') ?? '';
    const dateValue = form.watch('date');
    const timeValue = form.watch('time');
    const durationValue = form.watch('durationMinutes');
    const inviteesValue = form.watch('invitees');
    const descriptionValue = form.watch('description') ?? '';

    const startDate = useMemo(() => {
        if (!dateValue || !timeValue) return null;
        const start = new Date(`${dateValue}T${timeValue}`);
        return Number.isNaN(start.getTime()) ? null : start;
    }, [dateValue, timeValue]);

    const endDate = useMemo(() => {
        if (!startDate || !durationValue) return null;
        return new Date(startDate.getTime() + durationValue * 60_000);
    }, [startDate, durationValue]);

    const startTimeText = useMemo(() => {
        if (!startDate) return null;
        return startDate.toLocaleString(undefined, {
            weekday: 'short',
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
        });
    }, [startDate]);

    const endTimeText = useMemo(() => {
        if (!endDate) return null;
        return endDate.toLocaleTimeString(undefined, {
            hour: '2-digit',
            minute: '2-digit',
        });
    }, [endDate]);

    async function handleSubmit(values: ScheduleMeetingValues) {
        setServerError(null);

        const startTime = new Date(`${values.date}T${values.time}`);
        const endTime = new Date(
            startTime.getTime() + values.durationMinutes * 60_000,
        );

        try {
            const { data } = await scheduleMeeting({
                body: {
                    title: values.title ?? undefined,
                    description: values.description ?? undefined,
                    startTime: startTime.toISOString(),
                    endTime: endTime.toISOString(),
                    settings: mapSettingsToRequest(values.settings),
                    invitees:
                        values.invitees.length > 0
                            ? values.invitees.map((email) => ({ email }))
                            : undefined,
                },
                throwOnError: true,
            });

            setSuccessState({
                shortCode: data?.shortCode ?? '',
                title: data?.title ?? values.title ?? '',
                startTime: startTime.toLocaleString(),
            });
            form.reset();
        } catch (error) {
            if (error instanceof ApiFailError) {
                setServerError(error.message);
            } else if (error instanceof ApiError) {
                setServerError(t('errorServer'));
            } else {
                setServerError(t('errorNetwork'));
            }
        }
    }

    const isSubmitting = form.formState.isSubmitting;
    const descriptionTooLong = descriptionValue.length > DESCRIPTION_MAX_LENGTH;
    const inviteeCount = inviteesValue?.length ?? 0;
    const trimmedTitle = titleValue.trim();

    return (
        <WorkspaceShell activeTab='schedule' rightMode='search'>
            <section className='mx-auto w-full max-w-[1280px]'>
                <header className='max-w-[720px]'>
                    <h1 className='text-3xl font-semibold tracking-tight text-text-dark sm:text-4xl'>
                        {t('headline')}
                    </h1>
                    <p className='mt-2 text-base leading-7 text-text-secondary'>
                        {t('description')}
                    </p>
                </header>

                {serverError && (
                    <div className='mt-6 rounded-xl border border-error/40 bg-error-subtle px-4 py-3'>
                        <p className='text-sm text-error-dark' role='alert'>
                            {serverError}
                        </p>
                    </div>
                )}

                <Form {...form}>
                    <form
                        className='mt-8 grid gap-6 lg:grid-cols-5 lg:items-start'
                        onSubmit={form.handleSubmit(handleSubmit)}
                    >
                        <article className='rounded-2xl bg-surface p-6 shadow-[0_18px_48px_-32px_rgba(15,23,42,0.18)] sm:p-8 lg:col-span-3'>
                            <div className='space-y-6'>
                                <div className='flex flex-col gap-2'>
                                    <label
                                        className='text-sm font-semibold text-text-dark'
                                        htmlFor='schedule-title'
                                    >
                                        {t('topicLabel')}
                                    </label>
                                    <input
                                        className='h-11 w-full rounded-lg border border-border-input bg-surface px-4 text-base text-text-primary outline-none transition focus:border-primary focus:ring-2 focus:ring-primary'
                                        id='schedule-title'
                                        placeholder={t('topicPlaceholder')}
                                        type='text'
                                        {...form.register('title')}
                                    />
                                </div>

                                <div className='flex flex-col gap-2'>
                                    <label
                                        className='text-sm font-semibold text-text-dark'
                                        htmlFor='schedule-description'
                                    >
                                        {t('descriptionLabel')}
                                    </label>
                                    <textarea
                                        className='min-h-[96px] w-full resize-y rounded-lg border border-border-input bg-surface px-4 py-3 text-base text-text-primary outline-none transition focus:border-primary focus:ring-2 focus:ring-primary'
                                        id='schedule-description'
                                        maxLength={DESCRIPTION_MAX_LENGTH}
                                        placeholder={t(
                                            'descriptionPlaceholder',
                                        )}
                                        rows={3}
                                        {...form.register('description')}
                                    />
                                    <div className='flex items-center justify-between text-xs text-text-subtle'>
                                        <span
                                            className={
                                                descriptionTooLong
                                                    ? 'text-error-dark'
                                                    : ''
                                            }
                                            role={
                                                descriptionTooLong
                                                    ? 'alert'
                                                    : undefined
                                            }
                                        >
                                            {descriptionTooLong
                                                ? t('descriptionTooLong')
                                                : ''}
                                        </span>
                                        <span aria-live='polite'>
                                            {descriptionValue.length}/
                                            {DESCRIPTION_MAX_LENGTH}
                                        </span>
                                    </div>
                                </div>

                                <div className='grid gap-4 sm:grid-cols-3'>
                                    <div className='flex flex-col gap-2'>
                                        <label
                                            className='text-sm font-semibold text-text-dark'
                                            htmlFor='schedule-date'
                                        >
                                            {t('dateLabel')}
                                        </label>
                                        <div className='flex h-11 items-center rounded-lg border border-border-input bg-surface px-3 transition focus-within:border-primary focus-within:ring-2 focus-within:ring-primary'>
                                            <Calendar
                                                aria-hidden='true'
                                                className='mr-2 h-4 w-4 text-text-subtle'
                                            />
                                            <input
                                                className='w-full bg-transparent text-base text-text-primary outline-none'
                                                id='schedule-date'
                                                type='date'
                                                {...form.register('date')}
                                            />
                                        </div>
                                        {form.formState.errors.date && (
                                            <p
                                                className='text-xs text-error-dark'
                                                role='alert'
                                            >
                                                {form.formState.errors.date
                                                    .message
                                                === 'startTimeMustBeFuture'
                                                    ? t(
                                                          'validation.startTimeMustBeFuture',
                                                      )
                                                    : form.formState.errors.date
                                                          .message}
                                            </p>
                                        )}
                                    </div>

                                    <div className='flex flex-col gap-2'>
                                        <label
                                            className='text-sm font-semibold text-text-dark'
                                            htmlFor='schedule-time'
                                        >
                                            {t('timeLabel')}
                                        </label>
                                        <div className='flex h-11 items-center rounded-lg border border-border-input bg-surface px-3 transition focus-within:border-primary focus-within:ring-2 focus-within:ring-primary'>
                                            <Clock
                                                aria-hidden='true'
                                                className='mr-2 h-4 w-4 text-text-subtle'
                                            />
                                            <input
                                                className='w-full bg-transparent text-base text-text-primary outline-none'
                                                id='schedule-time'
                                                type='time'
                                                {...form.register('time')}
                                            />
                                        </div>
                                        {form.formState.errors.time && (
                                            <p
                                                className='text-xs text-error-dark'
                                                role='alert'
                                            >
                                                {
                                                    form.formState.errors.time
                                                        .message
                                                }
                                            </p>
                                        )}
                                    </div>

                                    <div className='flex flex-col gap-2'>
                                        <label
                                            className='text-sm font-semibold text-text-dark'
                                            htmlFor='schedule-duration'
                                        >
                                            {t('durationLabel')}
                                        </label>
                                        <div className='flex h-11 items-center rounded-lg border border-border-input bg-surface px-3 transition focus-within:border-primary focus-within:ring-2 focus-within:ring-primary'>
                                            <select
                                                className='w-full appearance-none bg-transparent text-base text-text-primary outline-none'
                                                id='schedule-duration'
                                                {...form.register(
                                                    'durationMinutes',
                                                    {
                                                        valueAsNumber: true,
                                                    },
                                                )}
                                            >
                                                {DURATION_OPTIONS.map(
                                                    (minutes) => (
                                                        <option
                                                            key={minutes}
                                                            value={minutes}
                                                        >
                                                            {t(
                                                                `duration${minutes}` as Parameters<
                                                                    typeof t
                                                                >[0],
                                                            )}
                                                        </option>
                                                    ),
                                                )}
                                            </select>
                                        </div>
                                    </div>
                                </div>

                                <div className='flex flex-col gap-2'>
                                    {/* biome-ignore lint/a11y/noLabelWithoutControl: InviteeInput is a composite widget; its inner input is focused via the container click handler */}
                                    <label className='text-sm font-semibold text-text-dark'>
                                        {t('inviteesLabel')}
                                    </label>
                                    <InviteeInput
                                        onChange={(emails) =>
                                            form.setValue('invitees', emails)
                                        }
                                        value={form.watch('invitees')}
                                    />
                                </div>
                            </div>
                        </article>

                        <aside className='space-y-6 lg:sticky lg:top-6 lg:col-span-2 lg:self-start'>
                            <section
                                aria-labelledby='schedule-summary-heading'
                                className='rounded-2xl border border-border-muted bg-surface p-5'
                            >
                                <h3
                                    className='text-sm font-semibold uppercase tracking-wider text-text-subtle'
                                    id='schedule-summary-heading'
                                >
                                    {t('summaryTitle')}
                                </h3>
                                <p className='mt-2 text-lg font-semibold text-text-dark'>
                                    {trimmedTitle || t('summaryEmptyTitle')}
                                </p>

                                <dl className='mt-4 space-y-3 text-sm'>
                                    <div className='flex items-start gap-3'>
                                        <Calendar
                                            aria-hidden='true'
                                            className='mt-0.5 h-4 w-4 text-text-subtle'
                                        />
                                        <div className='flex-1'>
                                            <dt className='text-xs text-text-subtle'>
                                                {t('summaryWhen')}
                                            </dt>
                                            <dd className='text-text-dark'>
                                                {startTimeText && endTimeText
                                                    ? `${startTimeText} – ${endTimeText}`
                                                    : t('summaryEmptySchedule')}
                                            </dd>
                                        </div>
                                    </div>
                                    <div className='flex items-start gap-3'>
                                        <Clock
                                            aria-hidden='true'
                                            className='mt-0.5 h-4 w-4 text-text-subtle'
                                        />
                                        <div className='flex-1'>
                                            <dt className='text-xs text-text-subtle'>
                                                {t('summaryDuration')}
                                            </dt>
                                            <dd className='text-text-dark'>
                                                {t(
                                                    `duration${durationValue}` as Parameters<
                                                        typeof t
                                                    >[0],
                                                )}
                                            </dd>
                                        </div>
                                    </div>
                                    <div className='flex items-start gap-3'>
                                        <Users
                                            aria-hidden='true'
                                            className='mt-0.5 h-4 w-4 text-text-subtle'
                                        />
                                        <div className='flex-1'>
                                            <dt className='text-xs text-text-subtle'>
                                                {t('summaryInvitees')}
                                            </dt>
                                            <dd className='text-text-dark'>
                                                {inviteeCount > 0
                                                    ? t(
                                                          'summaryInviteesCount',
                                                          {
                                                              count: inviteeCount,
                                                          },
                                                      )
                                                    : t('summaryInviteesEmpty')}
                                            </dd>
                                        </div>
                                    </div>
                                </dl>
                            </section>

                            <MeetingSettingsForm form={form} layout='split' />

                            <div className='space-y-3'>
                                <Button
                                    className='h-12 w-full rounded-lg text-base font-semibold'
                                    disabled={
                                        isSubmitting || descriptionTooLong
                                    }
                                    type='submit'
                                >
                                    {isSubmitting && (
                                        <Loader2 className='h-4 w-4 animate-spin' />
                                    )}
                                    {t('submit')}
                                </Button>
                                <p className='text-xs text-text-subtle'>
                                    {t('note')}
                                </p>
                                <p className='text-center text-xs text-text-subtle'>
                                    {t('footer')}
                                </p>
                            </div>
                        </aside>
                    </form>
                </Form>
            </section>

            {successState && (
                <Dialog
                    onOpenChange={(open) => !open && setSuccessState(null)}
                    open
                >
                    <DialogContent className='max-w-sm text-center'>
                        <DialogHeader className='items-center'>
                            <DialogTitle>{t('successTitle')}</DialogTitle>
                            <DialogDescription>
                                {t('successDescription', {
                                    title: successState.title,
                                    startTime: successState.startTime,
                                })}
                            </DialogDescription>
                        </DialogHeader>
                        <div className='rounded-xl bg-surface-input px-5 py-4'>
                            <p className='text-xs font-medium uppercase tracking-widest text-text-subtle'>
                                {t('meetingCode')}
                            </p>
                            <p className='mt-1 text-2xl font-semibold tracking-wider text-text-dark'>
                                {successState.shortCode}
                            </p>
                        </div>
                        <Button
                            className='mt-2 w-full'
                            onClick={() => setSuccessState(null)}
                            type='button'
                        >
                            {t('successDone')}
                        </Button>
                    </DialogContent>
                </Dialog>
            )}
        </WorkspaceShell>
    );
}
