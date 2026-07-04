'use client';

import { Eye, EyeOff, Loader2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
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
import { Button } from '@/components/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog.tsx';
import { useForgotPassword } from '@/hooks/use-forgot-password.ts';
import { useRecaptcha } from '@/hooks/use-recaptcha.ts';
import { OtpInput } from './otp-input.tsx';
import { PasswordStrengthMeter } from './password-strength-meter.tsx';

type ForgotPasswordModalProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onSuccess: (email: string) => void;
    labels: {
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

export function ForgotPasswordModal({
    open,
    onOpenChange,
    onSuccess,
    labels,
}: ForgotPasswordModalProps) {
    const {
        currentScreen,
        email,
        isLoading,
        error,
        otpExpirySeconds,
        resendCooldownSeconds,
        captchaRequired,
        submitEmail,
        submitEmailWithCaptcha,
        verifyOtpCode,
        resetPasswordWithToken,
        resendOtp,
        reset,
    } = useForgotPassword();

    const recaptcha = useRecaptcha();
    const recaptchaContainerRef = useRef<HTMLDivElement | null>(null);
    const submittedCaptchaTokenRef = useRef<string | null>(null);

    const [localEmail, setLocalEmail] = useState('');
    const [localOtp, setLocalOtp] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [passwordHidden, setPasswordHidden] = useState(true);
    const [confirmPasswordHidden, setConfirmPasswordHidden] = useState(true);
    const [showCancelConfirm, setShowCancelConfirm] = useState(false);
    const [validationError, setValidationError] = useState<string | null>(null);

    useEffect(() => {
        if (!open) {
            reset();
            setLocalEmail('');
            setLocalOtp('');
            setNewPassword('');
            setConfirmPassword('');
            setPasswordHidden(true);
            setConfirmPasswordHidden(true);
            setValidationError(null);
            submittedCaptchaTokenRef.current = null;
            recaptcha.reset();
        }
    }, [open, reset, recaptcha]);

    useEffect(() => {
        if (!captchaRequired || currentScreen !== 'email') return;
        const container = recaptchaContainerRef.current;
        if (!container) return;

        void recaptcha.render(container);
    }, [captchaRequired, currentScreen, recaptcha]);

    useEffect(() => {
        if (!captchaRequired || !recaptcha.token) return;
        if (submittedCaptchaTokenRef.current === recaptcha.token) return;

        submittedCaptchaTokenRef.current = recaptcha.token;
        void submitEmailWithCaptcha(localEmail, recaptcha.token).then(
            (success) => {
                if (success) {
                    recaptcha.reset();
                }
            },
        );
    }, [captchaRequired, localEmail, recaptcha, submitEmailWithCaptcha]);

    const handleClose = () => {
        if (currentScreen !== 'email') {
            setShowCancelConfirm(true);
        } else {
            onOpenChange(false);
        }
    };

    const handleConfirmCancel = () => {
        setShowCancelConfirm(false);
        onOpenChange(false);
    };

    const handleEmailSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setValidationError(null);

        if (!localEmail?.includes('@')) {
            setValidationError('Please enter a valid email address');
            return;
        }

        const success = await submitEmail(localEmail);
        if (!success) {
            // Error is already set in the hook
        }
    };

    const handleOtpComplete = async (otpValue: string) => {
        setValidationError(null);
        const success = await verifyOtpCode(otpValue);
        if (!success) {
            // Error is already set in the hook
        }
    };

    const handleVerifyOtp = async (e: React.FormEvent) => {
        e.preventDefault();
        setValidationError(null);

        if (localOtp.length !== 6) {
            setValidationError('Please enter the 6-digit code');
            return;
        }

        const success = await verifyOtpCode(localOtp);
        if (!success) {
            // Error is already set in the hook
        }
    };

    const handlePasswordReset = async (e: React.FormEvent) => {
        e.preventDefault();
        setValidationError(null);

        if (newPassword.length < 8) {
            setValidationError(labels.passwordTooShort);
            return;
        }

        if (newPassword !== confirmPassword) {
            setValidationError(labels.passwordsMismatch);
            return;
        }

        const success = await resetPasswordWithToken(newPassword);

        if (success) {
            onSuccess(email);
            onOpenChange(false);
        }
    };

    const formatTime = (seconds: number) => {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    };

    const stepText =
        currentScreen === 'email'
            ? ''
            : currentScreen === 'otp'
              ? labels.stepIndicator
                    .replace('{current}', '1')
                    .replace('{total}', '2')
              : labels.stepIndicator
                    .replace('{current}', '2')
                    .replace('{total}', '2');

    return (
        <>
            <Dialog open={open} onOpenChange={handleClose}>
                <DialogContent className='max-w-md'>
                    <DialogHeader>
                        <DialogTitle>{labels.title}</DialogTitle>
                        {stepText ? (
                            <p className='text-sm text-text-subtle'>
                                {stepText}
                            </p>
                        ) : null}
                    </DialogHeader>

                    {currentScreen === 'email' ? (
                        <form
                            onSubmit={handleEmailSubmit}
                            className='space-y-4'
                        >
                            <DialogDescription>
                                {labels.emailScreenDescription}
                            </DialogDescription>

                            {error || validationError ? (
                                <p
                                    className='rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-dark'
                                    role='alert'
                                >
                                    {error || validationError}
                                </p>
                            ) : null}

                            <div className='space-y-2'>
                                <label
                                    htmlFor='forgot-email'
                                    className='text-sm font-medium text-text-primary'
                                >
                                    {labels.emailLabel}
                                </label>
                                <input
                                    id='forgot-email'
                                    type='email'
                                    value={localEmail}
                                    onChange={(e) =>
                                        setLocalEmail(e.target.value)
                                    }
                                    placeholder={labels.emailPlaceholder}
                                    disabled={isLoading}
                                    className='h-12 w-full rounded-lg border border-border-input bg-surface-input-alt px-4 text-base text-text-primary outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20'
                                    autoFocus
                                />
                            </div>

                            {recaptcha.error ? (
                                <p
                                    className='rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-dark'
                                    role='alert'
                                >
                                    {recaptcha.error}
                                </p>
                            ) : null}

                            {captchaRequired ? (
                                <div
                                    ref={recaptchaContainerRef}
                                    className='flex justify-center'
                                />
                            ) : null}

                            <Button
                                type='submit'
                                disabled={isLoading}
                                className='w-full'
                            >
                                {isLoading ? (
                                    <Loader2 className='h-4 w-4 animate-spin' />
                                ) : (
                                    labels.sendCodeButton
                                )}
                            </Button>
                        </form>
                    ) : null}

                    {currentScreen === 'otp' ? (
                        <form onSubmit={handleVerifyOtp} className='space-y-4'>
                            <DialogDescription>
                                {labels.otpScreenDescription.replace(
                                    '{email}',
                                    email,
                                )}
                            </DialogDescription>

                            {error || validationError ? (
                                <p
                                    className='rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-dark'
                                    role='alert'
                                >
                                    {error || validationError}
                                </p>
                            ) : null}

                            <div className='space-y-2'>
                                <label
                                    htmlFor='otp-input'
                                    className='text-sm font-medium text-text-primary'
                                >
                                    {labels.otpLabel}
                                </label>
                                <OtpInput
                                    value={localOtp}
                                    onChange={setLocalOtp}
                                    error={!!error}
                                    disabled={isLoading}
                                    onComplete={handleOtpComplete}
                                />
                            </div>

                            <div className='space-y-2'>
                                <p
                                    className='text-sm text-text-secondary'
                                    aria-live='polite'
                                >
                                    {labels.expiresIn.replace(
                                        '{time}',
                                        formatTime(otpExpirySeconds),
                                    )}
                                </p>
                                {otpExpirySeconds <= 120 ? (
                                    <p
                                        className='text-sm text-warning'
                                        aria-live='assertive'
                                    >
                                        {labels.expiryWarning}
                                    </p>
                                ) : null}
                            </div>

                            <Button
                                type='submit'
                                disabled={isLoading || localOtp.length !== 6}
                                className='w-full'
                            >
                                {isLoading ? (
                                    <Loader2 className='h-4 w-4 animate-spin' />
                                ) : (
                                    labels.verifyButton
                                )}
                            </Button>

                            <Button
                                type='button'
                                variant='ghost'
                                disabled={
                                    isLoading || resendCooldownSeconds > 0
                                }
                                onClick={() => void resendOtp()}
                                className='w-full'
                            >
                                {resendCooldownSeconds > 0
                                    ? labels.resendCooldown.replace(
                                          '{seconds}',
                                          resendCooldownSeconds.toString(),
                                      )
                                    : labels.resendButton}
                            </Button>
                        </form>
                    ) : null}

                    {currentScreen === 'password' ? (
                        <form
                            onSubmit={handlePasswordReset}
                            className='space-y-4'
                        >
                            <DialogDescription>
                                {labels.passwordScreenDescription}
                            </DialogDescription>

                            {error || validationError ? (
                                <p
                                    className='rounded-lg bg-error-subtle px-4 py-3 text-sm text-error-dark'
                                    role='alert'
                                >
                                    {error || validationError}
                                </p>
                            ) : null}

                            <div className='space-y-2'>
                                <label
                                    htmlFor='new-password'
                                    className='text-sm font-medium text-text-primary'
                                >
                                    {labels.newPasswordLabel}
                                </label>
                                <div className='flex items-center gap-2 rounded-lg border border-border-input bg-surface-input-alt pr-2 transition focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20'>
                                    <input
                                        id='new-password'
                                        type={
                                            passwordHidden ? 'password' : 'text'
                                        }
                                        value={newPassword}
                                        onChange={(e) =>
                                            setNewPassword(e.target.value)
                                        }
                                        placeholder={
                                            labels.newPasswordPlaceholder
                                        }
                                        disabled={isLoading}
                                        className='h-12 flex-1 bg-transparent px-4 text-base text-text-primary outline-none'
                                        autoFocus
                                    />
                                    <Button
                                        type='button'
                                        variant='ghost'
                                        size='icon'
                                        onClick={() =>
                                            setPasswordHidden((v) => !v)
                                        }
                                        aria-label={
                                            passwordHidden
                                                ? labels.showPassword
                                                : labels.hidePassword
                                        }
                                        className='h-8 w-8'
                                    >
                                        {passwordHidden ? (
                                            <EyeOff className='h-4 w-4' />
                                        ) : (
                                            <Eye className='h-4 w-4' />
                                        )}
                                    </Button>
                                </div>
                                <PasswordStrengthMeter
                                    password={newPassword}
                                    labels={{
                                        weak: labels.strengthWeak,
                                        fair: labels.strengthFair,
                                        good: labels.strengthGood,
                                        strong: labels.strengthStrong,
                                    }}
                                />
                            </div>

                            <div className='space-y-2'>
                                <label
                                    htmlFor='confirm-password'
                                    className='text-sm font-medium text-text-primary'
                                >
                                    {labels.confirmPasswordLabel}
                                </label>
                                <div className='flex items-center gap-2 rounded-lg border border-border-input bg-surface-input-alt pr-2 transition focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20'>
                                    <input
                                        id='confirm-password'
                                        type={
                                            confirmPasswordHidden
                                                ? 'password'
                                                : 'text'
                                        }
                                        value={confirmPassword}
                                        onChange={(e) =>
                                            setConfirmPassword(e.target.value)
                                        }
                                        placeholder={
                                            labels.confirmPasswordPlaceholder
                                        }
                                        disabled={isLoading}
                                        className='h-12 flex-1 bg-transparent px-4 text-base text-text-primary outline-none'
                                    />
                                    <Button
                                        type='button'
                                        variant='ghost'
                                        size='icon'
                                        onClick={() =>
                                            setConfirmPasswordHidden((v) => !v)
                                        }
                                        aria-label={
                                            confirmPasswordHidden
                                                ? labels.showPassword
                                                : labels.hidePassword
                                        }
                                        className='h-8 w-8'
                                    >
                                        {confirmPasswordHidden ? (
                                            <EyeOff className='h-4 w-4' />
                                        ) : (
                                            <Eye className='h-4 w-4' />
                                        )}
                                    </Button>
                                </div>
                            </div>

                            <Button
                                type='submit'
                                disabled={isLoading}
                                className='w-full'
                            >
                                {isLoading ? (
                                    <Loader2 className='h-4 w-4 animate-spin' />
                                ) : (
                                    labels.resetButton
                                )}
                            </Button>
                        </form>
                    ) : null}
                </DialogContent>
            </Dialog>

            <AlertDialog
                open={showCancelConfirm}
                onOpenChange={setShowCancelConfirm}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>
                            {labels.cancelConfirmTitle}
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            {labels.cancelConfirmDescription}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>
                            {labels.cancelConfirmCancel}
                        </AlertDialogCancel>
                        <AlertDialogAction onClick={handleConfirmCancel}>
                            {labels.cancelConfirmConfirm}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
}
