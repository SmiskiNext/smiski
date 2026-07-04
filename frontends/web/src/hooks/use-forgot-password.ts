'use client';

import { useCallback, useEffect, useState } from 'react';
import {
    forgotPassword,
    resetPassword,
    verifyOtp,
} from '@/generated/sdk.gen.ts';
import { ApiFailError } from '@/lib/api/types.ts';

type Screen = 'email' | 'otp' | 'password';

type UseForgotPasswordState = {
    currentScreen: Screen;
    email: string;
    otp: string;
    temporaryToken: string | null;
    isLoading: boolean;
    error: string | null;
    otpExpirySeconds: number;
    resendCooldownSeconds: number;
    captchaRequired: boolean;
};

type UseForgotPasswordReturn = UseForgotPasswordState & {
    submitEmail: (email: string) => Promise<boolean>;
    submitEmailWithCaptcha: (
        email: string,
        captchaToken: string,
    ) => Promise<boolean>;
    verifyOtpCode: (otp: string) => Promise<boolean>;
    resetPasswordWithToken: (newPassword: string) => Promise<boolean>;
    resendOtp: () => Promise<void>;
    clearError: () => void;
    reset: () => void;
};

const OTP_EXPIRY_SECONDS = 20 * 60;
const RESEND_COOLDOWN_SECONDS = 120;
const CAPTCHA_REQUIRED = 'CAPTCHA_REQUIRED';
const CAPTCHA_INVALID = 'CAPTCHA_INVALID';

function getErrorMessage(error: unknown, fallback: string) {
    return error instanceof Error ? error.message : fallback;
}

export function useForgotPassword(): UseForgotPasswordReturn {
    const [state, setState] = useState<UseForgotPasswordState>({
        currentScreen: 'email',
        email: '',
        otp: '',
        temporaryToken: null,
        isLoading: false,
        error: null,
        otpExpirySeconds: 0,
        resendCooldownSeconds: 0,
        captchaRequired: false,
    });

    const requestForgotPassword = useCallback(
        (email: string, captchaToken?: string) =>
            forgotPassword({
                body: { email, captchaToken },
            }),
        [],
    );

    const completeForgotPasswordRequest = useCallback((email: string) => {
        setState((prev) => ({
            ...prev,
            email,
            currentScreen: 'otp',
            isLoading: false,
            error: null,
            captchaRequired: false,
            otpExpirySeconds: OTP_EXPIRY_SECONDS,
            resendCooldownSeconds: RESEND_COOLDOWN_SECONDS,
        }));
    }, []);

    const handleCaptchaError = useCallback((error: unknown) => {
        if (!(error instanceof ApiFailError)) {
            setState((prev) => ({
                ...prev,
                isLoading: false,
                error: getErrorMessage(error, 'Failed to send reset code'),
            }));
            return false;
        }

        if (error.code === CAPTCHA_REQUIRED) {
            setState((prev) => ({
                ...prev,
                isLoading: false,
                captchaRequired: true,
                error: null,
            }));
            return false;
        }

        if (error.code === CAPTCHA_INVALID) {
            setState((prev) => ({
                ...prev,
                isLoading: false,
                captchaRequired: true,
                error: error.message,
            }));
            return false;
        }

        setState((prev) => ({
            ...prev,
            isLoading: false,
            error: error.message,
        }));
        return false;
    }, []);

    useEffect(() => {
        if (state.otpExpirySeconds <= 0) return;

        const timer = setInterval(() => {
            setState((prev) => ({
                ...prev,
                otpExpirySeconds: Math.max(0, prev.otpExpirySeconds - 1),
            }));
        }, 1000);

        return () => clearInterval(timer);
    }, [state.otpExpirySeconds]);

    useEffect(() => {
        if (state.resendCooldownSeconds <= 0) return;

        const timer = setInterval(() => {
            setState((prev) => ({
                ...prev,
                resendCooldownSeconds: Math.max(
                    0,
                    prev.resendCooldownSeconds - 1,
                ),
            }));
        }, 1000);

        return () => clearInterval(timer);
    }, [state.resendCooldownSeconds]);

    const submitEmail = useCallback(
        async (email: string) => {
            setState((prev) => ({ ...prev, isLoading: true, error: null }));

            try {
                await requestForgotPassword(email);
                completeForgotPasswordRequest(email);
                return true;
            } catch (error) {
                return handleCaptchaError(error);
            }
        },
        [
            completeForgotPasswordRequest,
            handleCaptchaError,
            requestForgotPassword,
        ],
    );

    const submitEmailWithCaptcha = useCallback(
        async (email: string, captchaToken: string) => {
            setState((prev) => ({ ...prev, isLoading: true, error: null }));

            try {
                await requestForgotPassword(email, captchaToken);
                completeForgotPasswordRequest(email);
                return true;
            } catch (error) {
                return handleCaptchaError(error);
            }
        },
        [
            completeForgotPasswordRequest,
            handleCaptchaError,
            requestForgotPassword,
        ],
    );

    const verifyOtpCode = useCallback(
        async (otp: string) => {
            setState((prev) => ({ ...prev, isLoading: true, error: null }));

            try {
                const response = await verifyOtp({
                    body: { email: state.email, otp },
                });

                const temporaryToken = response.data?.temporaryToken;

                if (!temporaryToken) {
                    throw new Error('No temporary token received');
                }

                setState((prev) => ({
                    ...prev,
                    otp,
                    temporaryToken,
                    currentScreen: 'password',
                    isLoading: false,
                }));
                return true;
            } catch (error) {
                setState((prev) => ({
                    ...prev,
                    isLoading: false,
                    error: getErrorMessage(error, 'Invalid or expired code'),
                }));
                return false;
            }
        },
        [state.email],
    );

    const resetPasswordWithToken = useCallback(
        async (newPassword: string) => {
            if (!state.temporaryToken) {
                setState((prev) => ({
                    ...prev,
                    error: 'No temporary token available',
                }));
                return false;
            }

            setState((prev) => ({ ...prev, isLoading: true, error: null }));

            try {
                await resetPassword({
                    body: {
                        email: state.email,
                        temporaryToken: state.temporaryToken,
                        newPassword,
                    },
                });

                setState((prev) => ({ ...prev, isLoading: false }));
                return true;
            } catch (error) {
                setState((prev) => ({
                    ...prev,
                    isLoading: false,
                    error: getErrorMessage(error, 'Failed to reset password'),
                }));
                return false;
            }
        },
        [state.email, state.temporaryToken],
    );

    const resendOtp = useCallback(async () => {
        if (state.resendCooldownSeconds > 0) return;

        setState((prev) => ({ ...prev, isLoading: true, error: null }));

        try {
            await requestForgotPassword(state.email);

            setState((prev) => ({
                ...prev,
                isLoading: false,
                captchaRequired: false,
                otp: '',
                otpExpirySeconds: OTP_EXPIRY_SECONDS,
                resendCooldownSeconds: RESEND_COOLDOWN_SECONDS,
            }));
        } catch (error) {
            handleCaptchaError(error);
        }
    }, [
        handleCaptchaError,
        requestForgotPassword,
        state.email,
        state.resendCooldownSeconds,
    ]);

    const clearError = useCallback(() => {
        setState((prev) => ({ ...prev, error: null }));
    }, []);

    const reset = useCallback(() => {
        setState({
            currentScreen: 'email',
            email: '',
            otp: '',
            temporaryToken: null,
            isLoading: false,
            error: null,
            otpExpirySeconds: 0,
            resendCooldownSeconds: 0,
            captchaRequired: false,
        });
    }, []);

    return {
        ...state,
        submitEmail,
        submitEmailWithCaptcha,
        verifyOtpCode,
        resetPasswordWithToken,
        resendOtp,
        clearError,
        reset,
    };
}
