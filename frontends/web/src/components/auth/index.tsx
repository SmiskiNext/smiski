'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { LocaleToggle } from '@/components/shared/locale-toggle.tsx';
import { ThemeToggle } from '@/components/shared/theme-toggle.tsx';
import { googleLogin, login, register } from '@/generated/sdk.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import { setAuthCookies } from '@/lib/auth/cookies.ts';
import { setRememberMe as persistRememberMe } from '@/lib/auth/remember-me.ts';
import { AuthForm } from './form.tsx';
import { AuthHero } from './hero.tsx';

type AuthVariant = 'login' | 'register';

type AuthContainerProps = {
    variant: AuthVariant;
};

type FieldErrors = {
    email?: string;
    password?: string;
    username?: string;
};

const POPUP_ERROR_CODES = new Set([
    'auth/popup-closed-by-user',
    'auth/popup-blocked',
    'auth/cancelled-popup-request',
]);

export function AuthContainer({ variant }: AuthContainerProps) {
    const locale = useLocale();
    const t = useTranslations(`auth.${variant}`);
    const common = useTranslations('auth.common');
    const errorMessages = useTranslations('errors');
    const [loading, setLoading] = useState(false);
    const [googleLoading, setGoogleLoading] = useState(false);
    const [bannerError, setBannerError] = useState<string | null>(null);
    const [bannerSuccess, setBannerSuccess] = useState<string | null>(null);
    const [rememberMe, setRememberMe] = useState(false);
    const [serverFieldErrors, setServerFieldErrors] = useState<FieldErrors>({});
    const router = useRouter();
    const searchParams = useSearchParams();

    function clearErrors() {
        setBannerError(null);
        setBannerSuccess(null);
        setServerFieldErrors({});
    }

    function handleUserInteraction() {
        setBannerSuccess(null);
    }

    useEffect(() => {
        if (variant === 'login' && searchParams.get('registered') === '1') {
            setBannerSuccess(t('registeredSuccess'));
        }
        if (variant === 'login' && searchParams.get('passwordReset') === '1') {
            toast.success(t('passwordResetSuccess'));
        }
    }, [searchParams, t, variant]);

    async function handleEmailSubmit(
        email: string,
        password: string,
        name?: string,
        username?: string,
        rememberMeFlag?: boolean,
    ) {
        clearErrors();
        setLoading(true);

        try {
            if (variant === 'login') {
                const { data } = await login({
                    body: { email, password },
                    throwOnError: true,
                });

                if (data?.accessToken && data?.refreshToken) {
                    const shouldRemember = rememberMeFlag ?? false;
                    await setAuthCookies(
                        data.accessToken,
                        data.refreshToken,
                        shouldRemember,
                    );
                    persistRememberMe(shouldRemember);
                }
                router.push(`/${locale}/workspace`);
                return;
            }

            await register({
                body: {
                    email,
                    password,
                    fullName: name ?? '',
                    username: username ?? '',
                },
                throwOnError: true,
            });
            router.push(`/${locale}/login?registered=1`);
        } catch (error) {
            if (error instanceof ApiFailError) {
                if (error.errors.length > 0) {
                    const newFieldErrors: FieldErrors = {};
                    const bannerMessages: string[] = [];
                    for (const violation of error.errors) {
                        if (
                            violation.field === 'email'
                            || violation.field === 'password'
                            || violation.field === 'username'
                        ) {
                            newFieldErrors[violation.field] = violation.message;
                        } else {
                            bannerMessages.push(violation.message);
                        }
                    }
                    setServerFieldErrors(newFieldErrors);
                    if (bannerMessages.length > 0) {
                        setBannerError(bannerMessages.join(' '));
                    }
                }
                if (error.errors.length === 0) {
                    setBannerError(error.message);
                }
            } else if (error instanceof ApiError) {
                setBannerError(errorMessages('error_server'));
            } else {
                setBannerError(errorMessages('error_network'));
            }
        } finally {
            setLoading(false);
        }
    }

    async function handleGoogleSignIn() {
        clearErrors();
        setGoogleLoading(true);

        try {
            const { signInWithPopup } = await import('firebase/auth');
            const { auth, googleProvider } = await import('@/lib/firebase.ts');

            const result = await signInWithPopup(auth, googleProvider);
            const idToken = await result.user.getIdToken();

            const { data } = await googleLogin({
                body: { idToken },
                throwOnError: true,
            });

            if (data?.accessToken && data?.refreshToken) {
                await setAuthCookies(
                    data.accessToken,
                    data.refreshToken,
                    rememberMe,
                );
                persistRememberMe(rememberMe);
            }
            router.push(`/${locale}/workspace`);
        } catch (error: unknown) {
            console.error('[google-signin]', error);
            const firebaseError = error as { code?: string };
            if (
                firebaseError.code
                && POPUP_ERROR_CODES.has(firebaseError.code)
            ) {
                return;
            }

            if (error instanceof ApiFailError) {
                setBannerError(error.message);
            } else if (error instanceof ApiError) {
                setBannerError(errorMessages('error_server'));
            } else {
                const message = errorMessages('error_google_signin_failed');
                setBannerError(
                    process.env.NODE_ENV === 'production'
                        ? message
                        : `${message} [${firebaseError.code ?? 'unknown'}]`,
                );
            }
        } finally {
            setGoogleLoading(false);
        }
    }

    function handlePasswordResetSuccess(_email: string) {
        toast.success(t('passwordResetSuccess'));
        router.push(`/${locale}/login?passwordReset=1`);
    }

    const heroProps =
        variant === 'login'
            ? {
                  variant: 'login' as const,
                  brand: common('brand'),
                  brandHref: `/${locale}/home`,
                  eyebrow: t('eyebrow'),
                  brandHeadline: t('brand'),
                  heroDescription: t('heroDescription'),
              }
            : {
                  variant: 'register' as const,
                  brand: common('brand'),
                  brandHref: `/${locale}/home`,
                  heroLineOne: t('heroLineOne'),
                  heroLineTwo: t('heroLineTwo'),
                  heroLineThree: t('heroLineThree'),
                  heroDescription: t('heroDescription'),
                  securityCardTitle: t('securityCardTitle'),
                  securityCardDescription: t('securityCardDescription'),
              };

    return (
        <main className='min-h-screen bg-[linear-gradient(135deg,_var(--surface-hero-start)_0%,_var(--surface-hero-mid)_48%,_var(--surface-hero-end)_100%)] text-text-dark'>
            <div className='mx-auto flex min-h-screen max-w-[1660px] flex-col px-4 py-5 sm:px-8'>
                <div className='mb-4 flex items-center justify-end gap-3 sm:gap-4'>
                    <LocaleToggle namespace='auth.common' />
                    <ThemeToggle namespace='auth.common' />
                </div>
                <section className='flex flex-1 items-center'>
                    <div className='grid w-full gap-8 overflow-hidden rounded-[2.15rem] border border-white/80 bg-white/65 shadow-[0_30px_90px_-34px_rgba(15,23,42,0.22)] backdrop-blur lg:grid-cols-[1.12fr_0.88fr]'>
                        <AuthHero {...heroProps} />

                        <AuthForm
                            bannerError={bannerError}
                            bannerSuccess={bannerSuccess}
                            serverFieldErrors={serverFieldErrors}
                            googleLoading={googleLoading}
                            labels={{
                                title: t('title'),
                                subtitle: t('subtitle'),
                                nameLabel:
                                    variant === 'register'
                                        ? t('nameLabel')
                                        : undefined,
                                namePlaceholder:
                                    variant === 'register'
                                        ? t('namePlaceholder')
                                        : undefined,
                                usernameLabel:
                                    variant === 'register'
                                        ? t('usernameLabel')
                                        : undefined,
                                usernamePlaceholder:
                                    variant === 'register'
                                        ? t('usernamePlaceholder')
                                        : undefined,
                                usernameHelper:
                                    variant === 'register'
                                        ? t('usernameHelper')
                                        : undefined,
                                emailLabel: t('emailLabel'),
                                emailPlaceholder: t('emailPlaceholder'),
                                passwordLabel: t('passwordLabel'),
                                passwordPlaceholder: t('passwordPlaceholder'),
                                confirmPasswordLabel:
                                    variant === 'register'
                                        ? t('confirmPasswordLabel')
                                        : undefined,
                                confirmPasswordPlaceholder:
                                    variant === 'register'
                                        ? t('confirmPasswordPlaceholder')
                                        : undefined,
                                showPassword: t('showPassword'),
                                hidePassword: t('hidePassword'),
                                forgotPassword:
                                    variant === 'login'
                                        ? t('forgotPassword')
                                        : undefined,
                                rememberMe:
                                    variant === 'login'
                                        ? t('rememberMe')
                                        : undefined,
                                agreementPrefix:
                                    variant === 'register'
                                        ? t('agreementPrefix')
                                        : undefined,
                                agreementTerms:
                                    variant === 'register'
                                        ? t('agreementTerms')
                                        : undefined,
                                agreementMiddle:
                                    variant === 'register'
                                        ? t('agreementMiddle')
                                        : undefined,
                                agreementPrivacy:
                                    variant === 'register'
                                        ? t('agreementPrivacy')
                                        : undefined,
                                agreementSuffix:
                                    variant === 'register'
                                        ? t('agreementSuffix')
                                        : undefined,
                                submit: t('submit'),
                                divider: t('divider'),
                                google: t('google'),
                                switchPrefix: t('switchPrefix'),
                                switchAction: t('switchAction'),
                                forgotPasswordModal:
                                    variant === 'login'
                                        ? {
                                              title: t(
                                                  'forgotPasswordModal.title',
                                              ),
                                              emailScreenTitle: t(
                                                  'forgotPasswordModal.emailScreenTitle',
                                              ),
                                              emailScreenDescription: t(
                                                  'forgotPasswordModal.emailScreenDescription',
                                              ),
                                              emailLabel: t(
                                                  'forgotPasswordModal.emailLabel',
                                              ),
                                              emailPlaceholder: t(
                                                  'forgotPasswordModal.emailPlaceholder',
                                              ),
                                              sendCodeButton: t(
                                                  'forgotPasswordModal.sendCodeButton',
                                              ),
                                              otpScreenTitle: t(
                                                  'forgotPasswordModal.otpScreenTitle',
                                              ),
                                              otpScreenDescription: t(
                                                  'forgotPasswordModal.otpScreenDescription',
                                              ),
                                              otpLabel: t(
                                                  'forgotPasswordModal.otpLabel',
                                              ),
                                              verifyButton: t(
                                                  'forgotPasswordModal.verifyButton',
                                              ),
                                              resendButton: t(
                                                  'forgotPasswordModal.resendButton',
                                              ),
                                              resendCooldown: t(
                                                  'forgotPasswordModal.resendCooldown',
                                              ),
                                              expiresIn: t(
                                                  'forgotPasswordModal.expiresIn',
                                              ),
                                              expiryWarning: t(
                                                  'forgotPasswordModal.expiryWarning',
                                              ),
                                              passwordScreenTitle: t(
                                                  'forgotPasswordModal.passwordScreenTitle',
                                              ),
                                              passwordScreenDescription: t(
                                                  'forgotPasswordModal.passwordScreenDescription',
                                              ),
                                              newPasswordLabel: t(
                                                  'forgotPasswordModal.newPasswordLabel',
                                              ),
                                              newPasswordPlaceholder: t(
                                                  'forgotPasswordModal.newPasswordPlaceholder',
                                              ),
                                              confirmPasswordLabel: t(
                                                  'forgotPasswordModal.confirmPasswordLabel',
                                              ),
                                              confirmPasswordPlaceholder: t(
                                                  'forgotPasswordModal.confirmPasswordPlaceholder',
                                              ),
                                              resetButton: t(
                                                  'forgotPasswordModal.resetButton',
                                              ),
                                              showPassword: t(
                                                  'forgotPasswordModal.showPassword',
                                              ),
                                              hidePassword: t(
                                                  'forgotPasswordModal.hidePassword',
                                              ),
                                              stepIndicator: t(
                                                  'forgotPasswordModal.stepIndicator',
                                              ),
                                              cancelConfirmTitle: t(
                                                  'forgotPasswordModal.cancelConfirmTitle',
                                              ),
                                              cancelConfirmDescription: t(
                                                  'forgotPasswordModal.cancelConfirmDescription',
                                              ),
                                              cancelConfirmCancel: t(
                                                  'forgotPasswordModal.cancelConfirmCancel',
                                              ),
                                              cancelConfirmConfirm: t(
                                                  'forgotPasswordModal.cancelConfirmConfirm',
                                              ),
                                              passwordsMismatch: t(
                                                  'forgotPasswordModal.passwordsMismatch',
                                              ),
                                              passwordTooShort: t(
                                                  'forgotPasswordModal.passwordTooShort',
                                              ),
                                              strengthWeak: t(
                                                  'forgotPasswordModal.strengthWeak',
                                              ),
                                              strengthFair: t(
                                                  'forgotPasswordModal.strengthFair',
                                              ),
                                              strengthGood: t(
                                                  'forgotPasswordModal.strengthGood',
                                              ),
                                              strengthStrong: t(
                                                  'forgotPasswordModal.strengthStrong',
                                              ),
                                          }
                                        : undefined,
                            }}
                            loading={loading}
                            locale={locale}
                            onEmailSubmit={handleEmailSubmit}
                            onGoogleSignIn={handleGoogleSignIn}
                            onPasswordResetSuccess={handlePasswordResetSuccess}
                            onRememberMeChange={setRememberMe}
                            onUserInteraction={handleUserInteraction}
                            rememberMe={rememberMe}
                            variant={variant}
                        />
                    </div>
                </section>

                <footer className='mt-6 flex flex-col gap-4 border-t border-border-muted pt-6 text-base text-text-secondary sm:flex-row sm:items-center sm:justify-between'>
                    <p>
                        {common('copyright', {
                            year: new Date().getFullYear(),
                        })}
                    </p>
                    <nav className='flex flex-wrap items-center gap-7'>
                        <Link
                            className='hover:text-brand-blue'
                            href={`/${locale}/home`}
                        >
                            {common('privacy')}
                        </Link>
                        <Link
                            className='hover:text-brand-blue'
                            href={`/${locale}/home`}
                        >
                            {common('terms')}
                        </Link>
                        <Link
                            className='hover:text-brand-blue'
                            href={`/${locale}/home`}
                        >
                            {common('security')}
                        </Link>
                        <Link
                            className='hover:text-brand-blue'
                            href={`/${locale}/home`}
                        >
                            {common('contact')}
                        </Link>
                    </nav>
                </footer>
            </div>
        </main>
    );
}
