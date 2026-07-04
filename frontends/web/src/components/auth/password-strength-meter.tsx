'use client';

import { useMemo } from 'react';
import { cn } from '@/lib/utils.ts';

type PasswordStrength = 'weak' | 'fair' | 'good' | 'strong';

type PasswordStrengthMeterProps = {
    password: string;
    labels: {
        weak: string;
        fair: string;
        good: string;
        strong: string;
    };
};

function calculateStrength(password: string): PasswordStrength {
    if (password.length === 0) return 'weak';

    let score = 0;

    if (password.length >= 8) score++;
    if (password.length >= 12) score++;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
    if (/\d/.test(password)) score++;
    if (/[^a-zA-Z0-9]/.test(password)) score++;

    if (score <= 1) return 'weak';
    if (score === 2) return 'fair';
    if (score === 3 || score === 4) return 'good';
    return 'strong';
}

export function PasswordStrengthMeter({
    password,
    labels,
}: PasswordStrengthMeterProps) {
    const strength = useMemo(() => calculateStrength(password), [password]);

    const strengthConfig = {
        weak: { width: '25%', color: 'bg-error', label: labels.weak },
        fair: { width: '50%', color: 'bg-warning', label: labels.fair },
        good: { width: '75%', color: 'bg-info', label: labels.good },
        strong: { width: '100%', color: 'bg-success', label: labels.strong },
    };

    const config = strengthConfig[strength];

    if (password.length === 0) return null;

    return (
        <div className='space-y-2'>
            <div className='h-2 w-full overflow-hidden rounded-full bg-surface-input-alt'>
                <div
                    className={cn(
                        'h-full transition-all duration-300',
                        config.color,
                    )}
                    style={{ width: config.width }}
                    role='progressbar'
                    aria-valuenow={
                        strength === 'weak'
                            ? 25
                            : strength === 'fair'
                              ? 50
                              : strength === 'good'
                                ? 75
                                : 100
                    }
                    aria-valuemin={0}
                    aria-valuemax={100}
                />
            </div>
            <p
                className='text-sm text-text-secondary'
                aria-live='polite'
                aria-atomic='true'
            >
                {config.label}
            </p>
        </div>
    );
}
