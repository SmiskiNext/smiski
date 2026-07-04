'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Eye, EyeOff, Loader2 } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Button } from '@/components/ui/button.tsx';
import {
    Form,
    FormControl,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from '@/components/ui/form.tsx';
import { ForgotPasswordModal } from './forgot-password-modal.tsx';
import { GoogleSignInButton } from './social-button.tsx';

const loginSchema = z.object({
    email: z.string().min(1, 'required').email('invalidEmail'),
    password: z.string().min(1, 'required'),
});

const registerSchema = z
    .object({
        name: z.string().min(1, 'required').max(255, 'tooLong'),
        username: z
            .string()
            .min(1, 'required')
            .min(3, 'usernameTooShort')
            .max(30, 'usernameTooLong')
            .regex(/^[a-zA-Z0-9_-]+$/, 'usernameFormat'),
        email: z.string().min(1, 'required').email('invalidEmail'),
        password: z
            .string()
            .min(1, 'required')
            .min(8, 'passwordTooShort')
            .max(128, 'tooLong'),
        confirmPassword: z.string().min(1, 'required'),
    })
    .refine((data) => data.password === data.confirmPassword, {
        message: 'passwordsMismatch',
        path: ['confirmPassword'],
    });

type LoginValues = z.infer<typeof loginSchema>;
type RegisterValues = z.infer<typeof registerSchema>;
type AuthVariant = 'login' | 'register';

type AuthFormLabels = {
    title: string;
    subtitle: string;
    nameLabel?: string;
    namePlaceholder?: string;
    usernameLabel?: string;
    usernamePlaceholder?: string;
    usernameHelper?: string;
    emailLabel: string;
    emailPlaceholder: string;
    passwordLabel: string;
    passwordPlaceholder: string;
    confirmPasswordLabel?: string;
    confirmPasswordPlaceholder?: string;
    showPassword: string;
    hidePassword: string;
    forgotPassword?: string;
    rememberMe?: string;
    agreementPrefix?: string;
    agreementTerms?: string;
    agreementMiddle?: string;
    agreementPrivacy?: string;
    agreementSuffix?: string;
    submit: string;
    divider: string;
    google: string;
    switchPrefix: string;
    switchAction: string;
    forgotPasswordModal?: {
        title: string;
        emailScreenTitle: string;
        emailScreenDescription: string;
        emailLabel: string;
        emailPlaceholder: string;
        sendCodeButton: string;
        otpScreenTitle: string;
        otpScreenDescription: string;
        otpLabel: string;
        verifyButton: string;
        resendButton: string;
        resendCooldown: string;
        expiresIn: string;
        expiryWarning: string;
        passwordScreenTitle: string;
        passwordScreenDescription: string;
        newPasswordLabel: string;
        newPasswordPlaceholder: string;
        confirmPasswordLabel: string;
        confirmPasswordPlaceholder: string;
        resetButton: string;
        showPassword: string;
        hidePassword: string;
        stepIndicator: string;
        cancelConfirmTitle: string;
        cancelConfirmDescription: string;
        cancelConfirmCancel: string;
        cancelConfirmConfirm: string;
        passwordsMismatch: string;
        passwordTooShort: string;
        strengthWeak: string;
        strengthFair: string;
        strengthGood: string;
        strengthStrong: string;
    };
};

type AuthFormProps = {
    variant: AuthVariant;
    locale: string;
    labels: AuthFormLabels;
    onEmailSubmit: (
        email: string,
        password: string,
        name?: string,
        username?: string,
        rememberMe?: boolean,
    ) => Promise<void>;
    onGoogleSignIn: () => Promise<void>;
    onPasswordResetSuccess?: (email: string) => void;
    onRememberMeChange?: (rememberMe: boolean) => void;
    onUserInteraction: () => void;
    rememberMe?: boolean;
    loading: boolean;
    googleLoading: boolean;
    bannerError: string | null;
    bannerSuccess: string | null;
    serverFieldErrors: {
        email?: string;
        password?: string;
        username?: string;
    };
};

export function AuthForm({
    variant,
    locale,
    labels,
    onEmailSubmit,
    onGoogleSignIn,
    onPasswordResetSuccess,
    onRememberMeChange,
    onUserInteraction,
    loading,
    googleLoading,
    rememberMe = false,
    bannerError,
    bannerSuccess,
    serverFieldErrors,
}: AuthFormProps) {
    const [passwordHidden, setPasswordHidden] = useState(true);
    const [confirmPasswordHidden, setConfirmPasswordHidden] = useState(true);
    const [forgotPasswordOpen, setForgotPasswordOpen] = useState(false);
    const [prefilledEmail, setPrefilledEmail] = useState('');
    const isSubmitting = loading || googleLoading;

    const schema = variant === 'login' ? loginSchema : registerSchema;
    const form = useForm<LoginValues | RegisterValues>({
        resolver: zodResolver(schema),
        defaultValues:
            variant === 'login'
                ? { email: '', password: '' }
                : {
                      name: '',
                      username: '',
                      email: '',
                      password: '',
                      confirmPassword: '',
                  },
    });

    useEffect(() => {
        if (prefilledEmail && variant === 'login') {
            form.setValue('email', prefilledEmail);
        }
    }, [prefilledEmail, variant, form]);

    const handleForgotPasswordSuccess = (email: string) => {
        setPrefilledEmail(email);
        toast.success(
            'Password reset successful. You can now log in with your new password.',
        );
        if (onPasswordResetSuccess) {
            onPasswordResetSuccess(email);
        }
    };

    async function handleSubmit(values: LoginValues | RegisterValues) {
        const name = 'name' in values ? values.name : undefined;
        const username = 'username' in values ? values.username : undefined;
        await onEmailSubmit(
            values.email,
            values.password,
            name,
            username,
            variant === 'login' ? rememberMe : undefined,
        );
    }

    const emailError =
        form.formState.errors.email?.message ?? serverFieldErrors.email;
    const passwordError =
        form.formState.errors.password?.message ?? serverFieldErrors.password;
    const usernameError =
        (form.formState.errors as { username?: { message?: string } }).username
            ?.message ?? serverFieldErrors.username;
    const confirmPasswordError = (
        form.formState.errors as { confirmPassword?: { message?: string } }
    ).confirmPassword?.message;

    return (
        <div className='bg-surface px-6 py-8 sm:px-10 sm:py-12 lg:px-14 lg:py-16'>
            <div className='mx-auto flex h-full w-full max-w-[560px] flex-col'>
                <div>
                    <h2 className='text-5xl font-semibold leading-tight tracking-tight text-text-primary sm:text-[3.35rem]'>
                        {labels.title}
                    </h2>
                    <p className='mt-4 text-xl leading-8 text-text-secondary sm:text-[1.15rem]'>
                        {labels.subtitle}
                    </p>
                </div>

                <Form {...form}>
                    <form
                        className='mt-10 flex flex-col gap-7'
                        onChange={onUserInteraction}
                        onSubmit={form.handleSubmit(handleSubmit)}
                    >
                        {bannerSuccess ? (
                            <p
                                className='rounded-xl bg-success-subtle px-5 py-4 text-base text-success'
                                role='status'
                            >
                                {bannerSuccess}
                            </p>
                        ) : null}

                        {bannerError ? (
                            <p
                                className='rounded-xl bg-error-subtle px-5 py-4 text-base text-error-dark'
                                role='alert'
                            >
                                {bannerError}
                            </p>
                        ) : null}

                        {variant === 'register' && labels.nameLabel ? (
                            <FormField
                                control={form.control}
                                name='name'
                                defaultValue=''
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className='text-lg font-medium text-text-primary'>
                                            {labels.nameLabel}
                                        </FormLabel>
                                        <FormControl>
                                            <input
                                                className='h-16 w-full rounded-full bg-surface-input-alt px-6 text-xl text-text-primary outline-none ring-1 ring-transparent transition focus:ring-2 focus:ring-primary'
                                                placeholder={
                                                    labels.namePlaceholder
                                                }
                                                type='text'
                                                {...field}
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                        ) : null}

                        {variant === 'register' && labels.usernameLabel ? (
                            <FormField
                                control={form.control}
                                name='username'
                                defaultValue=''
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className='text-lg font-medium text-text-primary'>
                                            {labels.usernameLabel}
                                        </FormLabel>
                                        <FormControl>
                                            <input
                                                className={`h-16 w-full rounded-full bg-surface-input-alt px-6 text-xl text-text-primary outline-none ring-1 transition focus:ring-2 focus:ring-primary ${
                                                    usernameError
                                                        ? 'ring-error'
                                                        : 'ring-transparent'
                                                }`}
                                                id='auth-username'
                                                placeholder={
                                                    labels.usernamePlaceholder
                                                }
                                                type='text'
                                                autoComplete='username'
                                                {...field}
                                            />
                                        </FormControl>
                                        {usernameError ? (
                                            <p
                                                className='px-2 text-sm text-error-dark'
                                                role='alert'
                                            >
                                                {usernameError}
                                            </p>
                                        ) : labels.usernameHelper ? (
                                            <p className='px-2 text-sm text-text-subtle'>
                                                {labels.usernameHelper}
                                            </p>
                                        ) : null}
                                    </FormItem>
                                )}
                            />
                        ) : null}

                        <div className='flex flex-col gap-3'>
                            <label
                                className='text-lg font-medium uppercase tracking-[0.05em] text-text-primary'
                                htmlFor='auth-email'
                            >
                                {labels.emailLabel}
                            </label>
                            <input
                                className={`h-16 rounded-full bg-surface-input-alt px-6 text-xl text-text-primary outline-none ring-1 transition focus:ring-2 focus:ring-primary ${
                                    emailError
                                        ? 'ring-error'
                                        : 'ring-transparent'
                                }`}
                                id='auth-email'
                                placeholder={labels.emailPlaceholder}
                                type='email'
                                {...form.register('email')}
                            />
                            {emailError ? (
                                <p
                                    className='px-2 text-sm text-error-dark'
                                    role='alert'
                                >
                                    {emailError}
                                </p>
                            ) : null}
                        </div>

                        <div className='flex flex-col gap-3'>
                            <div className='flex items-center justify-between gap-4'>
                                <label
                                    className='text-lg font-medium uppercase tracking-[0.05em] text-text-primary'
                                    htmlFor='auth-password'
                                >
                                    {labels.passwordLabel}
                                </label>
                                {variant === 'login'
                                && labels.forgotPassword ? (
                                    <button
                                        type='button'
                                        className='text-base font-medium text-brand-blue transition-colors hover:text-primary-dark'
                                        onClick={() =>
                                            setForgotPasswordOpen(true)
                                        }
                                    >
                                        {labels.forgotPassword}
                                    </button>
                                ) : null}
                            </div>
                            <div
                                className={`flex h-16 items-center rounded-full bg-surface-input-alt pr-5 ring-1 transition focus-within:ring-2 focus-within:ring-primary ${
                                    passwordError
                                        ? 'ring-error'
                                        : 'ring-transparent'
                                }`}
                            >
                                <input
                                    className='h-full w-full bg-transparent px-6 text-xl text-text-primary outline-none'
                                    id='auth-password'
                                    placeholder={labels.passwordPlaceholder}
                                    type={passwordHidden ? 'password' : 'text'}
                                    {...form.register('password')}
                                />
                                {variant === 'register' ? (
                                    <Button
                                        aria-label={
                                            passwordHidden
                                                ? labels.showPassword
                                                : labels.hidePassword
                                        }
                                        className='rounded-full text-text-subtle hover:bg-surface hover:text-brand-blue'
                                        onClick={() =>
                                            setPasswordHidden((v) => !v)
                                        }
                                        size='icon'
                                        type='button'
                                        variant='ghost'
                                    >
                                        {passwordHidden ? (
                                            <EyeOff className='h-6 w-6' />
                                        ) : (
                                            <Eye className='h-6 w-6' />
                                        )}
                                    </Button>
                                ) : null}
                            </div>
                            {passwordError ? (
                                <p
                                    className='px-2 text-sm text-error-dark'
                                    role='alert'
                                >
                                    {passwordError}
                                </p>
                            ) : null}
                        </div>

                        {variant === 'register'
                        && labels.confirmPasswordLabel ? (
                            <FormField
                                control={form.control}
                                name='confirmPassword'
                                defaultValue=''
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel className='text-lg font-medium uppercase tracking-[0.05em] text-text-primary'>
                                            {labels.confirmPasswordLabel}
                                        </FormLabel>
                                        <FormControl>
                                            <div
                                                className={`flex h-16 items-center rounded-full bg-surface-input-alt pr-5 ring-1 transition focus-within:ring-2 focus-within:ring-primary ${
                                                    confirmPasswordError
                                                        ? 'ring-error'
                                                        : 'ring-transparent'
                                                }`}
                                            >
                                                <input
                                                    className='h-full w-full bg-transparent px-6 text-xl text-text-primary outline-none'
                                                    id='auth-confirm-password'
                                                    placeholder={
                                                        labels.confirmPasswordPlaceholder
                                                    }
                                                    type={
                                                        confirmPasswordHidden
                                                            ? 'password'
                                                            : 'text'
                                                    }
                                                    autoComplete='new-password'
                                                    {...field}
                                                />
                                                <Button
                                                    aria-label={
                                                        confirmPasswordHidden
                                                            ? labels.showPassword
                                                            : labels.hidePassword
                                                    }
                                                    className='rounded-full text-text-subtle hover:bg-surface hover:text-brand-blue'
                                                    onClick={() =>
                                                        setConfirmPasswordHidden(
                                                            (v) => !v,
                                                        )
                                                    }
                                                    size='icon'
                                                    type='button'
                                                    variant='ghost'
                                                >
                                                    {confirmPasswordHidden ? (
                                                        <EyeOff className='h-6 w-6' />
                                                    ) : (
                                                        <Eye className='h-6 w-6' />
                                                    )}
                                                </Button>
                                            </div>
                                        </FormControl>
                                        {confirmPasswordError ? (
                                            <p
                                                className='px-2 text-sm text-error-dark'
                                                role='alert'
                                            >
                                                {confirmPasswordError}
                                            </p>
                                        ) : null}
                                    </FormItem>
                                )}
                            />
                        ) : null}

                        {variant === 'login' && labels.rememberMe ? (
                            <label
                                className='flex items-center gap-3 text-base text-text-secondary'
                                htmlFor='auth-remember-me'
                            >
                                <input
                                    checked={rememberMe}
                                    className='h-6 w-6 rounded-md border border-border-input accent-primary'
                                    id='auth-remember-me'
                                    onChange={(event) => {
                                        onRememberMeChange?.(
                                            event.target.checked,
                                        );
                                        onUserInteraction();
                                    }}
                                    type='checkbox'
                                />
                                <span>{labels.rememberMe}</span>
                            </label>
                        ) : null}

                        {variant === 'register' && labels.agreementPrefix ? (
                            <label className='mt-1 flex items-start gap-4 text-lg leading-9 text-text-secondary'>
                                <input
                                    className='mt-1 h-6 w-6 rounded-md border border-border-input accent-primary'
                                    type='checkbox'
                                />
                                <span>
                                    {labels.agreementPrefix}{' '}
                                    <Link
                                        className='font-medium text-brand-blue hover:text-primary-dark'
                                        href={`/${locale}/register`}
                                    >
                                        {labels.agreementTerms}
                                    </Link>{' '}
                                    {labels.agreementMiddle}{' '}
                                    <Link
                                        className='font-medium text-brand-blue hover:text-primary-dark'
                                        href={`/${locale}/register`}
                                    >
                                        {labels.agreementPrivacy}
                                    </Link>{' '}
                                    {labels.agreementSuffix}
                                </span>
                            </label>
                        ) : null}

                        <button
                            className='mt-2 flex h-16 items-center justify-center rounded-full bg-[linear-gradient(135deg,_var(--primary)_0%,_var(--primary-hover)_100%)] px-8 text-2xl font-semibold text-white shadow-[0_24px_50px_-26px_rgba(26,115,232,0.95)] transition-all hover:-translate-y-0.5 hover:shadow-[0_28px_55px_-24px_rgba(26,115,232,1)] disabled:opacity-60 disabled:hover:translate-y-0'
                            disabled={isSubmitting}
                            type='submit'
                        >
                            {loading ? (
                                <Loader2 className='h-5 w-5 animate-spin' />
                            ) : (
                                labels.submit
                            )}
                        </button>
                    </form>
                </Form>

                <div className='mt-9'>
                    <div className='flex items-center gap-5 text-base uppercase tracking-[0.18em] text-text-subtle'>
                        <span className='h-px flex-1 bg-border-muted' />
                        <span>{labels.divider}</span>
                        <span className='h-px flex-1 bg-border-muted' />
                    </div>

                    <GoogleSignInButton
                        disabled={isSubmitting}
                        label={labels.google}
                        loading={googleLoading}
                        onClick={() => void onGoogleSignIn()}
                    />
                </div>

                <p className='mt-10 text-center text-[1.15rem] leading-8 text-text-secondary'>
                    {labels.switchPrefix}{' '}
                    <Link
                        className='font-semibold text-brand-blue hover:text-primary-dark'
                        href={`/${locale}/${variant === 'login' ? 'register' : 'login'}`}
                    >
                        {labels.switchAction}
                    </Link>
                </p>

                {variant === 'login' && labels.forgotPasswordModal ? (
                    <ForgotPasswordModal
                        open={forgotPasswordOpen}
                        onOpenChange={setForgotPasswordOpen}
                        onSuccess={handleForgotPasswordSuccess}
                        labels={labels.forgotPasswordModal}
                    />
                ) : null}
            </div>
        </div>
    );
}
