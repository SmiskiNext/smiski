'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
    Avatar,
    AvatarFallback,
    AvatarImage,
} from '@/components/ui/avatar.tsx';
import { Button } from '@/components/ui/button.tsx';
import type { UserManagementUserResponse } from '@/generated/types.gen.ts';
import type { AccountProfileFormValues } from './schema.ts';
import {
    AVATAR_ALLOWED_TYPES,
    AVATAR_MAX_SIZE_BYTES,
    accountProfileSchema,
} from './schema.ts';
import type { SavePhase } from './types.ts';

type AccountSettingsFormProps = {
    profile: UserManagementUserResponse;
    saveState: SavePhase;
    saveErrorMessage: string | null;
    onSave: (
        payload: {
            fullName: string;
            username: string;
            avatarUrl?: string;
        },
        errorFallback: string,
    ) => Promise<void>;
};

function getInitials(name: string | undefined): string {
    if (!name) return '?';
    return name
        .split(' ')
        .map((part) => part[0] ?? '')
        .join('')
        .toUpperCase()
        .slice(0, 2);
}

function AvatarPreview({
    profile,
    previewUrl,
}: {
    profile: UserManagementUserResponse;
    previewUrl: string | null;
}) {
    const t = useTranslations('workspace.accountSettings');
    const imageSrc = previewUrl ?? profile.avatarUrl ?? undefined;
    const imageAlt = previewUrl
        ? t('avatarPreviewAlt')
        : (profile.fullName ?? t('avatarPreviewAlt'));

    return (
        <Avatar className='h-36 w-36'>
            {imageSrc ? <AvatarImage alt={imageAlt} src={imageSrc} /> : null}
            <AvatarFallback className='bg-[linear-gradient(135deg,_var(--avatar-gradient-navy-start)_0%,_var(--avatar-gradient-navy-end)_100%)] text-5xl font-semibold text-white'>
                {getInitials(profile.fullName)}
            </AvatarFallback>
        </Avatar>
    );
}

export function AccountSettingsForm({
    profile,
    saveState,
    saveErrorMessage,
    onSave,
}: AccountSettingsFormProps) {
    const t = useTranslations('workspace.accountSettings');
    const errors = useTranslations('errors');
    const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(
        null,
    );
    const [avatarFileError, setAvatarFileError] = useState<string | null>(null);
    const avatarInputRef = useRef<HTMLInputElement | null>(null);
    const previewUrlRef = useRef<string | null>(null);

    const {
        register,
        handleSubmit,
        reset,
        setValue,
        formState: { isDirty, errors: formErrors },
    } = useForm<AccountProfileFormValues>({
        resolver: zodResolver(accountProfileSchema),
        defaultValues: {
            fullName: profile.fullName ?? '',
            username: profile.username ?? '',
            avatarUrl: profile.avatarUrl ?? '',
        },
    });

    const handleAvatarChange = useCallback(
        (event: React.ChangeEvent<HTMLInputElement>) => {
            setAvatarFileError(null);
            const file = event.target.files?.[0];
            if (!file) return;

            if (!AVATAR_ALLOWED_TYPES.includes(file.type)) {
                setAvatarFileError(t('avatarInvalidType'));
                if (avatarInputRef.current) {
                    avatarInputRef.current.value = '';
                }
                return;
            }

            if (file.size > AVATAR_MAX_SIZE_BYTES) {
                setAvatarFileError(t('avatarFileTooLarge'));
                if (avatarInputRef.current) {
                    avatarInputRef.current.value = '';
                }
                return;
            }

            if (previewUrlRef.current) {
                URL.revokeObjectURL(previewUrlRef.current);
            }
            const objectUrl = URL.createObjectURL(file);
            previewUrlRef.current = objectUrl;
            setAvatarPreviewUrl(objectUrl);
            setValue('avatarUrl', objectUrl, { shouldDirty: true });
        },
        [t, setValue],
    );

    const clearAvatarPreview = useCallback(() => {
        if (previewUrlRef.current) {
            URL.revokeObjectURL(previewUrlRef.current);
            previewUrlRef.current = null;
        }
        setAvatarPreviewUrl(null);
        setAvatarFileError(null);
        if (avatarInputRef.current) {
            avatarInputRef.current.value = '';
        }
        setValue('avatarUrl', profile.avatarUrl ?? '', { shouldDirty: true });
    }, [profile.avatarUrl, setValue]);

    useEffect(() => {
        return () => {
            if (previewUrlRef.current) {
                URL.revokeObjectURL(previewUrlRef.current);
            }
        };
    }, []);

    const onFormSubmit = handleSubmit((values) => {
        const payload = {
            fullName: values.fullName.trim(),
            username: values.username.trim(),
            avatarUrl: values.avatarUrl?.trim() || undefined,
        };
        void onSave(payload, t('saveError'));
    });

    const handleCancel = useCallback(() => {
        clearAvatarPreview();
        reset({
            fullName: profile.fullName ?? '',
            username: profile.username ?? '',
            avatarUrl: profile.avatarUrl ?? '',
        });
    }, [clearAvatarPreview, reset, profile]);

    const isSaving = saveState === 'SAVING';
    const showSuccess = saveState === 'SUCCESS';
    const showFormError = saveState === 'ERROR' && saveErrorMessage;

    return (
        <form className='space-y-8' onSubmit={onFormSubmit} noValidate>
            <div className='flex flex-col items-center text-center'>
                <AvatarPreview
                    previewUrl={avatarPreviewUrl}
                    profile={profile}
                />

                <div className='mt-4 flex flex-col items-center gap-2'>
                    <Button
                        className='rounded-full'
                        onClick={() => avatarInputRef.current?.click()}
                        type='button'
                        variant='outline'
                    >
                        {t('changeAvatar')}
                    </Button>
                    <input
                        ref={avatarInputRef}
                        accept={AVATAR_ALLOWED_TYPES.join(',')}
                        className='hidden'
                        type='file'
                        onChange={handleAvatarChange}
                    />
                    {(avatarPreviewUrl || profile.avatarUrl) && (
                        <Button
                            className='text-error'
                            onClick={clearAvatarPreview}
                            type='button'
                            variant='link'
                        >
                            {t('removeAvatar')}
                        </Button>
                    )}
                </div>

                {avatarFileError && (
                    <p className='mt-2 text-sm text-error'>{avatarFileError}</p>
                )}

                <p className='mt-2 text-xs text-text-message-time'>
                    {t('avatarHint')}
                </p>
            </div>

            <div className='space-y-5'>
                <div>
                    <label
                        className='mb-2 block text-sm font-medium text-text-secondary'
                        htmlFor='fullName'
                    >
                        {t('fullNameLabel')}
                    </label>
                    <input
                        {...register('fullName')}
                        aria-describedby={
                            formErrors.fullName ? 'fullName-error' : undefined
                        }
                        aria-invalid={formErrors.fullName ? 'true' : undefined}
                        className='w-full rounded-xl border border-border-muted bg-surface px-4 py-3 text-text-dark outline-none ring-1 ring-transparent transition focus:border-primary focus:ring-2 focus:ring-primary'
                        id='fullName'
                        placeholder={t('fullNamePlaceholder')}
                        type='text'
                    />
                    {formErrors.fullName && (
                        <p
                            className='mt-1.5 text-sm text-error'
                            id='fullName-error'
                        >
                            {formErrors.fullName.message
                            === 'validation_fullName_too_long'
                                ? errors('validation_too_long')
                                : errors('validation_required')}
                        </p>
                    )}
                </div>

                <div>
                    <label
                        className='mb-2 block text-sm font-medium text-text-secondary'
                        htmlFor='username'
                    >
                        {t('usernameLabel')}
                    </label>
                    <input
                        {...register('username')}
                        aria-describedby={
                            formErrors.username ? 'username-error' : undefined
                        }
                        aria-invalid={formErrors.username ? 'true' : undefined}
                        className='w-full rounded-xl border border-border-muted bg-surface px-4 py-3 text-text-dark outline-none ring-1 ring-transparent transition focus:border-primary focus:ring-2 focus:ring-primary'
                        id='username'
                        placeholder={t('usernamePlaceholder')}
                        type='text'
                    />
                    {formErrors.username && (
                        <p
                            className='mt-1.5 text-sm text-error'
                            id='username-error'
                        >
                            {formErrors.username.message
                            === 'validation_username_too_short'
                                ? errors('validation_too_short')
                                : formErrors.username.message
                                    === 'validation_username_too_long'
                                  ? errors('validation_too_long')
                                  : formErrors.username.message
                                      === 'validation_username_invalid_chars'
                                    ? errors('validation_invalid_format')
                                    : errors('validation_required')}
                        </p>
                    )}
                </div>

                <div>
                    <label
                        className='mb-2 block text-sm font-medium text-text-secondary'
                        htmlFor='email'
                    >
                        {t('emailLabel')}
                    </label>
                    <input
                        className='w-full cursor-not-allowed rounded-xl border border-border-muted bg-surface-alt px-4 py-3 text-text-message-time outline-none'
                        defaultValue={profile.email ?? ''}
                        disabled
                        id='email'
                        type='email'
                    />
                    <p className='mt-1.5 text-xs text-text-message-time'>
                        {t('emailReadOnly')}
                    </p>
                </div>
            </div>

            {showFormError && (
                <div className='rounded-xl border border-error-dark bg-error-subtle px-4 py-3'>
                    <p className='text-sm text-error'>{saveErrorMessage}</p>
                </div>
            )}

            {showSuccess && (
                <div className='rounded-xl border border-success bg-success-subtle px-4 py-3'>
                    <p className='text-sm text-success'>{t('saveSuccess')}</p>
                </div>
            )}

            <div className='flex justify-end gap-3'>
                {isDirty && !isSaving && (
                    <Button
                        onClick={handleCancel}
                        type='button'
                        variant='outline'
                    >
                        {t('cancel')}
                    </Button>
                )}
                <Button
                    disabled={isSaving || (!isDirty && !showFormError)}
                    type='submit'
                >
                    {isSaving ? t('saving') : t('saveChanges')}
                </Button>
            </div>
        </form>
    );
}
