'use client';

import {
    ChevronDown,
    Lock,
    MessageSquare,
    Mic,
    Monitor,
    Users,
    Video,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import {
    FormControl,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from '@/components/ui/form.tsx';
import { Switch } from '@/components/ui/switch.tsx';
import { cn } from '@/lib/utils.ts';

type MeetingSettingsLayout = 'flat' | 'split' | 'grid';

type MeetingSettingsFormProps = {
    // biome-ignore lint/suspicious/noExplicitAny: form types are co-/contra-variant; callers with superset field types need this cast
    form: UseFormReturn<any>;
    layout?: MeetingSettingsLayout;
    disabled?: boolean;
};

export function MeetingSettingsForm({
    form,
    layout = 'flat',
    disabled = false,
}: MeetingSettingsFormProps) {
    const t = useTranslations('meetingSettings');
    const tSchedule = useTranslations('workspace.schedule');
    const [advancedOpen, setAdvancedOpen] = useState(false);

    const waitingRoomField = (
        <ToggleRow
            description={t('waitingRoomDescription')}
            disabled={disabled}
            form={form}
            icon={<Users aria-hidden='true' className='h-5 w-5 text-primary' />}
            label={t('waitingRoom')}
            layout={layout}
            name='settings.waitingRoom'
        />
    );
    const allowGuestField = (
        <ToggleRow
            description={t('allowGuestDescription')}
            disabled={disabled}
            form={form}
            icon={<Users aria-hidden='true' className='h-5 w-5 text-primary' />}
            label={t('allowGuest')}
            layout={layout}
            name='settings.allowGuest'
        />
    );
    const passwordField = (
        <PasswordField disabled={disabled} form={form} layout={layout} t={t} />
    );

    const screenShareField = (
        <ToggleRow
            description={t('screenShareDescription')}
            disabled={disabled}
            form={form}
            icon={
                <Monitor aria-hidden='true' className='h-5 w-5 text-primary' />
            }
            label={t('screenShare')}
            layout={layout}
            name='settings.allowScreenShare'
        />
    );
    const chatField = (
        <ToggleRow
            description={t('chatDescription')}
            disabled={disabled}
            form={form}
            icon={
                <MessageSquare
                    aria-hidden='true'
                    className='h-5 w-5 text-primary'
                />
            }
            label={t('chat')}
            layout={layout}
            name='settings.chatEnabled'
        />
    );
    const microphoneField = (
        <ToggleRow
            description={t('microphoneDescription')}
            disabled={disabled}
            form={form}
            icon={<Mic aria-hidden='true' className='h-5 w-5 text-primary' />}
            label={t('microphone')}
            layout={layout}
            name='settings.allowMicrophone'
        />
    );
    const videoField = (
        <ToggleRow
            description={t('videoDescription')}
            disabled={disabled}
            form={form}
            icon={<Video aria-hidden='true' className='h-5 w-5 text-primary' />}
            label={t('video')}
            layout={layout}
            name='settings.allowVideo'
        />
    );
    const maxParticipantsField = (
        <MaxParticipantsField
            disabled={disabled}
            form={form}
            layout={layout}
            t={t}
        />
    );

    if (layout === 'grid') {
        return (
            <div className='space-y-5'>
                <div className='grid grid-cols-1 gap-3 md:grid-cols-2'>
                    {waitingRoomField}
                    {allowGuestField}
                    {passwordField}
                </div>

                <div className='rounded-2xl border border-border-muted bg-surface'>
                    <button
                        aria-controls='advanced-settings-grid-panel'
                        aria-expanded={advancedOpen}
                        className='flex w-full items-center gap-3 rounded-2xl px-4 py-4 text-left transition-colors hover:bg-surface-input'
                        onClick={() => setAdvancedOpen((prev) => !prev)}
                        type='button'
                    >
                        <div className='flex-1'>
                            <p className='text-sm font-semibold text-text-dark'>
                                {tSchedule('advancedSettings')}
                            </p>
                            <p className='text-xs text-text-subtle'>
                                {tSchedule('advancedSettingsDescription')}
                            </p>
                        </div>
                        <ChevronDown
                            aria-hidden='true'
                            className={cn(
                                'h-5 w-5 text-text-subtle transition-transform duration-200',
                                advancedOpen && 'rotate-180',
                            )}
                        />
                    </button>
                    {advancedOpen && (
                        <div
                            className='grid grid-cols-1 gap-3 border-t border-border-muted p-3 md:grid-cols-2'
                            id='advanced-settings-grid-panel'
                        >
                            {maxParticipantsField}
                            {screenShareField}
                            {chatField}
                            {microphoneField}
                            {videoField}
                        </div>
                    )}
                </div>
            </div>
        );
    }

    if (layout === 'split') {
        return (
            <div className='space-y-6'>
                <div>
                    <h3 className='text-base font-semibold text-text-dark'>
                        {t('title')}
                    </h3>
                    <div className='mt-3 divide-y divide-border-muted rounded-2xl border border-border-muted bg-surface'>
                        {waitingRoomField}
                        {allowGuestField}
                        {passwordField}
                    </div>
                </div>

                <div className='rounded-2xl border border-border-muted bg-surface'>
                    <button
                        aria-controls='advanced-settings-panel'
                        aria-expanded={advancedOpen}
                        className='flex w-full items-center gap-3 rounded-2xl px-4 py-4 text-left transition-colors hover:bg-surface-input'
                        onClick={() => setAdvancedOpen((prev) => !prev)}
                        type='button'
                    >
                        <div className='flex-1'>
                            <p className='text-sm font-semibold text-text-dark'>
                                {tSchedule('advancedSettings')}
                            </p>
                            <p className='text-xs text-text-subtle'>
                                {tSchedule('advancedSettingsDescription')}
                            </p>
                        </div>
                        <ChevronDown
                            aria-hidden='true'
                            className={cn(
                                'h-5 w-5 text-text-subtle transition-transform duration-200',
                                advancedOpen && 'rotate-180',
                            )}
                        />
                    </button>
                    {advancedOpen && (
                        <div
                            className='divide-y divide-border-muted border-t border-border-muted'
                            id='advanced-settings-panel'
                        >
                            {maxParticipantsField}
                            {screenShareField}
                            {chatField}
                            {microphoneField}
                            {videoField}
                        </div>
                    )}
                </div>
            </div>
        );
    }

    return (
        <div className='space-y-4'>
            <h3 className='text-base font-semibold text-text-dark'>
                {t('title')}
            </h3>

            <div className='space-y-3'>
                {waitingRoomField}
                {allowGuestField}
                {screenShareField}
                {chatField}
                {microphoneField}
                {videoField}
                {maxParticipantsField}
                {passwordField}
            </div>
        </div>
    );
}

type ToggleRowProps = {
    // biome-ignore lint/suspicious/noExplicitAny: shared with parent form generic
    form: UseFormReturn<any>;
    name: string;
    label: string;
    description: string;
    icon: React.ReactNode;
    layout: MeetingSettingsLayout;
    disabled?: boolean;
};

function ToggleRow({
    form,
    name,
    label,
    description,
    icon,
    layout,
    disabled = false,
}: ToggleRowProps) {
    const containerClass =
        layout === 'split'
            ? 'flex items-center justify-between gap-3 px-4 py-4'
            : layout === 'grid'
              ? 'flex h-full items-center justify-between gap-3 rounded-xl bg-surface-input p-4'
              : 'flex items-center justify-between rounded-xl bg-surface-input p-4';

    return (
        <FormField
            control={form.control}
            name={name}
            render={({ field }) => (
                <FormItem>
                    <div className={containerClass}>
                        <div className='flex items-center gap-3'>
                            {icon}
                            <div>
                                <FormLabel className='text-sm font-semibold text-text-dark'>
                                    {label}
                                </FormLabel>
                                <p className='text-xs text-text-subtle'>
                                    {description}
                                </p>
                            </div>
                        </div>
                        <FormControl>
                            <Switch
                                checked={field.value}
                                disabled={disabled}
                                onCheckedChange={field.onChange}
                            />
                        </FormControl>
                    </div>
                    <FormMessage />
                </FormItem>
            )}
        />
    );
}

type FieldRowProps = {
    // biome-ignore lint/suspicious/noExplicitAny: shared with parent form generic
    form: UseFormReturn<any>;
    layout: MeetingSettingsLayout;
    t: ReturnType<typeof useTranslations<'meetingSettings'>>;
    disabled?: boolean;
};

function MaxParticipantsField({
    form,
    layout,
    t,
    disabled = false,
}: FieldRowProps) {
    const containerClass =
        layout === 'split'
            ? 'px-4 py-4'
            : layout === 'grid'
              ? 'rounded-xl bg-surface-input p-4'
              : 'rounded-xl bg-surface-input p-4';

    return (
        <FormField
            control={form.control}
            name='settings.maxParticipants'
            render={({ field }) => (
                <FormItem>
                    <div className={containerClass}>
                        <div className='flex items-center gap-3'>
                            <Users
                                aria-hidden='true'
                                className='h-5 w-5 text-primary'
                            />
                            <FormLabel className='text-sm font-semibold text-text-dark'>
                                {t('maxParticipants')}
                            </FormLabel>
                        </div>
                        <FormControl>
                            <input
                                className='mt-3 h-10 w-full rounded-lg border border-border-input bg-surface px-3 text-sm text-text-primary outline-none ring-transparent transition focus:ring-2 focus:ring-primary disabled:cursor-not-allowed disabled:opacity-60'
                                disabled={disabled}
                                max={500}
                                min={2}
                                onChange={(e) =>
                                    field.onChange(Number(e.target.value))
                                }
                                type='number'
                                value={field.value}
                            />
                        </FormControl>
                        <FormMessage />
                    </div>
                </FormItem>
            )}
        />
    );
}

function PasswordField({ form, layout, t, disabled = false }: FieldRowProps) {
    const containerClass =
        layout === 'split'
            ? 'px-4 py-4'
            : layout === 'grid'
              ? 'rounded-xl bg-surface-input p-4'
              : 'rounded-xl bg-surface-input p-4';

    return (
        <FormField
            control={form.control}
            name='settings.password'
            render={({ field }) => (
                <FormItem>
                    <div className={containerClass}>
                        <div className='flex items-center gap-3'>
                            <Lock
                                aria-hidden='true'
                                className='h-5 w-5 text-primary'
                            />
                            <FormLabel className='text-sm font-semibold text-text-dark'>
                                {t('password')}
                            </FormLabel>
                        </div>
                        <FormControl>
                            <input
                                className='mt-3 h-10 w-full rounded-lg border border-border-input bg-surface px-3 text-sm text-text-primary outline-none ring-transparent transition focus:ring-2 focus:ring-primary disabled:cursor-not-allowed disabled:opacity-60'
                                disabled={disabled}
                                onChange={field.onChange}
                                placeholder={t('passwordPlaceholder')}
                                type='password'
                                value={field.value ?? ''}
                            />
                        </FormControl>
                        <FormMessage />
                    </div>
                </FormItem>
            )}
        />
    );
}
