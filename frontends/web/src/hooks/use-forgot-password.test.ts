import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as sdk from '@/generated/sdk.gen.ts';
import { useForgotPassword } from './use-forgot-password.ts';

vi.mock('@/generated/sdk.gen', () => ({
    forgotPassword: vi.fn(),
    verifyOtp: vi.fn(),
    resetPassword: vi.fn(),
}));

describe('useForgotPassword', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.clearAllMocks();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('initializes with email screen', () => {
        const { result } = renderHook(() => useForgotPassword());
        expect(result.current.currentScreen).toBe('email');
        expect(result.current.email).toBe('');
        expect(result.current.isLoading).toBe(false);
    });

    it('transitions to otp screen after successful email submission', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        expect(result.current.currentScreen).toBe('otp');
        expect(result.current.email).toBe('test@example.com');
        expect(result.current.otpExpirySeconds).toBe(1200);
        expect(result.current.resendCooldownSeconds).toBe(120);
    });

    it('handles email submission error', async () => {
        vi.mocked(sdk.forgotPassword).mockRejectedValue(
            new Error('Network error'),
        );

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        expect(result.current.currentScreen).toBe('email');
        expect(result.current.error).toBe('Network error');
    });

    it('transitions to password screen after successful OTP verification', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });
        vi.mocked(sdk.verifyOtp).mockResolvedValue({
            data: { temporaryToken: 'test-token-123' },
        });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        await act(async () => {
            await result.current.verifyOtpCode('123456');
        });

        expect(result.current.currentScreen).toBe('password');
        expect(result.current.temporaryToken).toBe('test-token-123');
        expect(result.current.otp).toBe('123456');
    });

    it('handles OTP verification error', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });
        vi.mocked(sdk.verifyOtp).mockRejectedValue(new Error('Invalid OTP'));

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        await act(async () => {
            await result.current.verifyOtpCode('000000');
        });

        expect(result.current.currentScreen).toBe('otp');
        expect(result.current.error).toBe('Invalid OTP');
    });

    it('calls resetPassword with temporaryToken', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });
        vi.mocked(sdk.verifyOtp).mockResolvedValue({
            data: { temporaryToken: 'test-token-123' },
        });
        vi.mocked(sdk.resetPassword).mockResolvedValue({ data: undefined });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        await act(async () => {
            await result.current.verifyOtpCode('123456');
        });

        await act(async () => {
            await result.current.resetPasswordWithToken('NewPassword123!');
        });

        expect(sdk.resetPassword).toHaveBeenCalledWith({
            body: {
                email: 'test@example.com',
                temporaryToken: 'test-token-123',
                newPassword: 'NewPassword123!',
            },
        });
    });

    it('decrements OTP expiry timer every second', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        expect(result.current.otpExpirySeconds).toBe(1200);

        await act(async () => {
            vi.advanceTimersByTime(5000);
        });

        expect(result.current.otpExpirySeconds).toBe(1195);
    });

    it('decrements resend cooldown timer every second', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        expect(result.current.resendCooldownSeconds).toBe(120);

        await act(async () => {
            vi.advanceTimersByTime(3000);
        });

        expect(result.current.resendCooldownSeconds).toBe(117);
    });

    it('resends OTP and resets timers', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        await act(async () => {
            vi.advanceTimersByTime(121000);
        });

        expect(result.current.resendCooldownSeconds).toBe(0);

        await act(async () => {
            await result.current.resendOtp();
        });

        expect(result.current.otpExpirySeconds).toBe(1200);
        expect(result.current.resendCooldownSeconds).toBe(120);
        expect(result.current.otp).toBe('');
    });

    it('clears error when clearError is called', async () => {
        vi.mocked(sdk.forgotPassword).mockRejectedValue(
            new Error('Test error'),
        );

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        expect(result.current.error).toBe('Test error');

        act(() => {
            result.current.clearError();
        });

        expect(result.current.error).toBeNull();
    });

    it('resets all state when reset is called', async () => {
        vi.mocked(sdk.forgotPassword).mockResolvedValue({ data: undefined });

        const { result } = renderHook(() => useForgotPassword());

        await act(async () => {
            await result.current.submitEmail('test@example.com');
        });

        act(() => {
            result.current.reset();
        });

        expect(result.current.currentScreen).toBe('email');
        expect(result.current.email).toBe('');
        expect(result.current.otp).toBe('');
        expect(result.current.temporaryToken).toBeNull();
        expect(result.current.otpExpirySeconds).toBe(0);
        expect(result.current.resendCooldownSeconds).toBe(0);
    });
});
