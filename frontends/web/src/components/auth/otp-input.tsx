'use client';

import { useEffect, useRef, useState } from 'react';
import { cn } from '@/lib/utils.ts';

type OtpInputProps = {
    value: string;
    onChange: (value: string) => void;
    length?: number;
    error?: boolean;
    disabled?: boolean;
    onComplete?: (value: string) => void;
};

export function OtpInput({
    value,
    onChange,
    length = 6,
    error = false,
    disabled = false,
    onComplete,
}: OtpInputProps) {
    const [localValue, setLocalValue] = useState(value.split(''));
    const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
    const [shake, setShake] = useState(false);

    useEffect(() => {
        setLocalValue(value.split('').slice(0, length));
    }, [value, length]);

    useEffect(() => {
        if (error) {
            setShake(true);
            const timer = setTimeout(() => setShake(false), 500);
            return () => clearTimeout(timer);
        }
    }, [error]);

    const handleChange = (index: number, digit: string) => {
        if (disabled) return;

        const newDigit = digit.replace(/[^0-9]/g, '');
        if (newDigit.length > 1) return;

        const newValue = [...localValue];
        newValue[index] = newDigit;
        setLocalValue(newValue);

        const newOtp = newValue.join('');
        onChange(newOtp);

        if (newDigit && index < length - 1) {
            inputRefs.current[index + 1]?.focus();
        }

        if (newOtp.length === length && onComplete) {
            onComplete(newOtp);
        }
    };

    const handleKeyDown = (
        index: number,
        event: React.KeyboardEvent<HTMLInputElement>,
    ) => {
        if (disabled) return;

        if (event.key === 'Backspace') {
            if (!localValue[index] && index > 0) {
                inputRefs.current[index - 1]?.focus();
            } else {
                const newValue = [...localValue];
                newValue[index] = '';
                setLocalValue(newValue);
                onChange(newValue.join(''));
            }
        } else if (event.key === 'ArrowLeft' && index > 0) {
            event.preventDefault();
            inputRefs.current[index - 1]?.focus();
        } else if (event.key === 'ArrowRight' && index < length - 1) {
            event.preventDefault();
            inputRefs.current[index + 1]?.focus();
        }
    };

    const handlePaste = (event: React.ClipboardEvent<HTMLInputElement>) => {
        if (disabled) return;

        event.preventDefault();
        const pastedData = event.clipboardData.getData('text/plain');
        const digits = pastedData.replace(/[^0-9]/g, '').slice(0, length);

        if (digits.length === 0) return;

        const newValue = digits.split('');
        setLocalValue(newValue);
        onChange(newValue.join(''));

        const focusIndex = Math.min(digits.length, length - 1);
        inputRefs.current[focusIndex]?.focus();

        if (digits.length === length && onComplete) {
            onComplete(digits);
        }
    };

    const handleFocus = (event: React.FocusEvent<HTMLInputElement>) => {
        event.target.select();
    };

    return (
        <div
            className={cn(
                'flex gap-2 justify-center',
                shake && 'animate-shake',
            )}
        >
            {Array.from({ length }).map((_, index) => (
                <input
                    key={index}
                    ref={(el) => {
                        inputRefs.current[index] = el;
                    }}
                    type='text'
                    inputMode='numeric'
                    maxLength={1}
                    value={localValue[index] || ''}
                    onChange={(e) => handleChange(index, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(index, e)}
                    onPaste={handlePaste}
                    onFocus={handleFocus}
                    disabled={disabled}
                    aria-label={`Digit ${index + 1} of ${length}`}
                    className={cn(
                        'h-14 w-12 rounded-lg border-2 bg-surface-input-alt text-center text-2xl font-semibold text-text-primary outline-none transition-all',
                        'focus:border-primary focus:ring-2 focus:ring-primary/20',
                        error
                            ? 'border-error ring-2 ring-error/20'
                            : 'border-border-input',
                        disabled && 'cursor-not-allowed opacity-50',
                    )}
                />
            ))}
        </div>
    );
}
