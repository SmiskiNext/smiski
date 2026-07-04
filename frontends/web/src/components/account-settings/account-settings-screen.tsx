'use client';

import { useLocale, useTranslations } from 'next-intl';
import {
    Avatar,
    AvatarFallback,
    AvatarImage,
} from '@/components/ui/avatar.tsx';
import { Button } from '@/components/ui/button.tsx';
import { WorkspaceShell } from '@/components/workspace-shell.tsx';
import { AccountSettingsForm } from './account-settings-form.tsx';
import { DeleteAccountDialog } from './delete-account-dialog.tsx';
import { useUserAccountSettings } from './use-user-account-settings.ts';

function LoadingState() {
    const t = useTranslations('workspace.accountSettings');
    return (
        <div className='flex flex-col items-center justify-center py-24 text-center'>
            <div className='h-12 w-12 animate-spin rounded-full border-4 border-border-muted border-t-primary' />
            <p className='mt-6 text-lg text-text-muted'>{t('loading')}</p>
        </div>
    );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
    const t = useTranslations('workspace.accountSettings');
    return (
        <div className='flex flex-col items-center justify-center py-24 text-center'>
            <div className='flex h-16 w-16 items-center justify-center rounded-full bg-error-subtle'>
                <svg
                    aria-hidden='true'
                    className='h-8 w-8 text-error'
                    fill='none'
                    stroke='currentColor'
                    strokeWidth='2'
                    viewBox='0 0 24 24'
                >
                    <path d='M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z' />
                </svg>
            </div>
            <p className='mt-6 text-lg font-medium text-text-dark'>
                {t('loadError')}
            </p>
            <p className='mt-2 text-base text-text-muted'>
                {t('loadErrorDescription')}
            </p>
            <Button className='mt-8' onClick={onRetry} type='button'>
                {t('retry')}
            </Button>
        </div>
    );
}

function getInitials(name: string | undefined): string {
    if (!name) return '?';
    return name
        .split(' ')
        .map((part) => part[0] ?? '')
        .join('')
        .toUpperCase()
        .slice(0, 2);
}

function LogoutButton({
    isLoggingOut,
    errorMessage,
    onLogout,
}: {
    isLoggingOut: boolean;
    errorMessage: string | null;
    onLogout: () => void;
}) {
    const t = useTranslations('workspace.accountSettings');

    return (
        <div className='rounded-[1.7rem] bg-surface p-7 shadow-[0_22px_60px_-38px_rgba(15,23,42,0.2)]'>
            <div className='flex items-center justify-between'>
                <div className='flex items-center gap-4'>
                    <span className='flex h-12 w-12 items-center justify-center rounded-full bg-error-subtle text-error'>
                        <svg
                            aria-hidden='true'
                            className='h-6 w-6'
                            fill='none'
                            stroke='currentColor'
                            strokeWidth='2'
                            viewBox='0 0 24 24'
                        >
                            <path d='M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4' />
                            <path d='M10 17l5-5-5-5' />
                            <path d='M15 12H3' />
                        </svg>
                    </span>
                    <div>
                        <h3 className='text-[1.55rem] font-semibold tracking-tight text-text-dark'>
                            {t('logoutTitle')}
                        </h3>
                        <p className='mt-1 text-base text-text-muted'>
                            {t('logoutDescription')}
                        </p>
                    </div>
                </div>
                <Button
                    disabled={isLoggingOut}
                    onClick={onLogout}
                    type='button'
                    variant='outline'
                >
                    {isLoggingOut ? t('loggingOut') : t('logoutAction')}
                </Button>
            </div>
            {errorMessage && (
                <div className='mt-4 rounded-xl border border-error-dark bg-error-subtle px-4 py-3'>
                    <p className='text-sm text-error'>{errorMessage}</p>
                </div>
            )}
        </div>
    );
}

function DeleteAccountSection({ onOpen }: { onOpen: () => void }) {
    const t = useTranslations('workspace.accountSettings');

    return (
        <div className='rounded-[1.7rem] bg-surface p-7 shadow-[0_22px_60px_-38px_rgba(15,23,42,0.2)]'>
            <div className='flex items-center justify-between'>
                <div className='flex items-center gap-4'>
                    <span className='flex h-12 w-12 items-center justify-center rounded-full bg-error-subtle text-error'>
                        <svg
                            aria-hidden='true'
                            className='h-6 w-6'
                            fill='none'
                            stroke='currentColor'
                            strokeWidth='2'
                            viewBox='0 0 24 24'
                        >
                            <path d='m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0' />
                        </svg>
                    </span>
                    <div>
                        <h3 className='text-[1.55rem] font-semibold tracking-tight text-error-dark'>
                            {t('deleteAccountSectionTitle')}
                        </h3>
                        <p className='mt-1 text-base text-text-muted'>
                            {t('deleteAccountSectionDescription')}
                        </p>
                    </div>
                </div>
                <Button onClick={onOpen} type='button' variant='destructive'>
                    {t('deleteAccountAction')}
                </Button>
            </div>
        </div>
    );
}

export function AccountSettingsScreen() {
    const locale = useLocale();
    const t = useTranslations('workspace.accountSettings');
    const {
        state,
        saveState,
        saveErrorMessage,
        logoutState,
        logoutErrorMessage,
        dialogState,
        actions,
    } = useUserAccountSettings();

    return (
        <WorkspaceShell activeTab='profile' rightMode='search'>
            <section className='mx-auto max-w-[1320px]'>
                <div className='max-w-[760px]'>
                    <h1 className='text-5xl font-semibold tracking-tight text-text-dark sm:text-6xl'>
                        {t('headline')}
                    </h1>
                    <p className='mt-5 text-2xl leading-9 text-text-secondary sm:text-[1.1rem]'>
                        {t('description')}
                    </p>
                </div>

                {state.phase === 'LOADING' && <LoadingState />}
                {state.phase === 'ERROR' && (
                    <ErrorState onRetry={actions.retry} />
                )}
                {state.phase === 'SUCCESS' && state.profile && (
                    <div className='mt-12 grid gap-8 xl:grid-cols-[0.82fr_1.18fr]'>
                        <article className='rounded-[2rem] bg-surface p-8 shadow-[0_26px_70px_-38px_rgba(15,23,42,0.2)] sm:p-10'>
                            <div className='flex flex-col items-center text-center'>
                                <Avatar className='h-36 w-36 shadow-[0_24px_50px_-28px_rgba(26,115,232,0.95)]'>
                                    {state.profile.avatarUrl ? (
                                        <AvatarImage
                                            alt={
                                                state.profile.fullName
                                                ?? t('avatarPreviewAlt')
                                            }
                                            src={state.profile.avatarUrl}
                                        />
                                    ) : null}
                                    <AvatarFallback className='bg-[linear-gradient(135deg,_var(--avatar-gradient-navy-start)_0%,_var(--avatar-gradient-navy-end)_100%)] text-5xl font-semibold text-white'>
                                        {getInitials(state.profile.fullName)}
                                    </AvatarFallback>
                                </Avatar>
                                <h2 className='mt-8 text-[2.35rem] font-semibold tracking-tight text-text-dark'>
                                    {state.profile.fullName || '—'}
                                </h2>
                                <p className='mt-2 text-xl text-text-muted'>
                                    {state.profile.email || '—'}
                                </p>
                                {state.profile.authProvider && (
                                    <div className='mt-4 inline-flex items-center gap-2 rounded-full bg-primary-subtle px-4 py-1.5'>
                                        <svg
                                            aria-hidden='true'
                                            className='h-4 w-4 text-primary'
                                            fill='none'
                                            stroke='currentColor'
                                            strokeWidth='2'
                                            viewBox='0 0 24 24'
                                        >
                                            <path d='M15 7a2 2 0 0 1 2 2m4 0a6 6 0 0 1-7.743 5.743L11 17H9v2H7v2H4a1 1 0 0 1-1-1v-2.586a1 1 0 0 1 .293-.707l5.964-5.964A6 6 0 1 1 21 9Z' />
                                        </svg>
                                        <span className='text-sm font-medium text-primary'>
                                            {(state.profile.authProvider
                                                ? (
                                                      t as (
                                                          key: string,
                                                      ) => string
                                                  )(
                                                      `authProviderLabels.${state.profile.authProvider}`,
                                                  )
                                                : null)
                                                ?? state.profile.authProvider}
                                        </span>
                                    </div>
                                )}
                            </div>

                            <div className='mt-10 rounded-[1.6rem] bg-surface-alt p-6'>
                                <p className='text-sm font-semibold uppercase tracking-[0.18em] text-primary'>
                                    {t('usernameLabel')}
                                </p>
                                <p className='mt-3 text-[1.7rem] font-semibold text-text-dark'>
                                    @{state.profile.username || '—'}
                                </p>
                                {state.profile.createdAt && (
                                    <p className='mt-4 text-sm text-text-message-time'>
                                        {t('memberSince', {
                                            date: new Date(
                                                state.profile
                                                    .createdAt as string,
                                            ).toLocaleDateString(),
                                        })}
                                    </p>
                                )}
                            </div>
                        </article>

                        <div className='space-y-6'>
                            <div className='rounded-[2rem] bg-surface p-8 shadow-[0_26px_70px_-38px_rgba(15,23,42,0.2)] sm:p-10'>
                                <h3 className='text-xl font-semibold text-text-dark'>
                                    {t('editProfileTitle')}
                                </h3>
                                <p className='mt-2 mb-8 text-base text-text-muted'>
                                    {t('editProfileDescription')}
                                </p>
                                <AccountSettingsForm
                                    profile={state.profile}
                                    saveErrorMessage={saveErrorMessage}
                                    saveState={saveState}
                                    onSave={actions.save}
                                />
                            </div>

                            <LogoutButton
                                errorMessage={logoutErrorMessage}
                                isLoggingOut={logoutState === 'LOGGING_OUT'}
                                onLogout={() =>
                                    void actions.logout(
                                        locale,
                                        t('logoutError'),
                                    )
                                }
                            />

                            <DeleteAccountSection
                                onOpen={actions.openDeleteDialog}
                            />
                        </div>
                    </div>
                )}
            </section>

            <DeleteAccountDialog
                dialogState={dialogState}
                onClose={actions.closeDeleteDialog}
                onConfirm={(errorFallback) =>
                    (async () => {
                        await actions.confirmDelete(locale, errorFallback);
                    })()
                }
            />
        </WorkspaceShell>
    );
}
