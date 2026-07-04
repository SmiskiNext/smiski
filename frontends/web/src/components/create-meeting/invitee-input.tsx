'use client';

import { X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useRef, useState } from 'react';
import { cn } from '@/lib/utils.ts';

type InviteeInputProps = {
    value: string[];
    onChange: (emails: string[]) => void;
    className?: string;
};

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function InviteeInput({
    value,
    onChange,
    className,
}: InviteeInputProps) {
    const t = useTranslations('meetingSettings');
    const [inputValue, setInputValue] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);

    function addInvitee(email: string) {
        const trimmed = email.trim();
        if (!trimmed || !EMAIL_REGEX.test(trimmed)) return;
        if (value.includes(trimmed)) return;
        onChange([...value, trimmed]);
        setInputValue('');
    }

    function removeInvitee(email: string) {
        onChange(value.filter((e) => e !== email));
    }

    function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
        if (event.key === 'Enter') {
            event.preventDefault();
            addInvitee(inputValue);
        }
    }

    function handleBlur() {
        if (inputValue.trim()) {
            addInvitee(inputValue);
        }
    }

    return (
        // biome-ignore lint/a11y/noStaticElementInteractions: tag-input container; clicking empty space focuses the hidden text input
        // biome-ignore lint/a11y/useKeyWithClickEvents: keyboard users interact with the input element directly
        <div
            className={cn(
                'min-h-[3rem] w-full rounded-xl border border-border-input bg-surface-input p-3 focus-within:ring-2 focus-within:ring-primary',
                className,
            )}
            onClick={() => inputRef.current?.focus()}
        >
            <div className='flex flex-wrap gap-2'>
                {value.map((email) => (
                    <span
                        className='inline-flex items-center gap-1.5 rounded-full bg-primary-muted px-3 py-1 text-sm font-medium text-primary'
                        key={email}
                    >
                        {email}
                        <button
                            aria-label={t('removeInvitee', { email })}
                            className='inline-flex h-4 w-4 items-center justify-center rounded-full text-primary transition-colors hover:bg-primary hover:text-white'
                            onClick={(e) => {
                                e.stopPropagation();
                                removeInvitee(email);
                            }}
                            type='button'
                        >
                            <X className='h-3 w-3' />
                        </button>
                    </span>
                ))}
                <input
                    ref={inputRef}
                    className='min-w-[200px] flex-1 bg-transparent text-sm text-text-primary outline-none placeholder:text-text-subtle'
                    onBlur={handleBlur}
                    onChange={(e) => setInputValue(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={
                        value.length === 0 ? t('inviteesPlaceholder') : ''
                    }
                    type='email'
                    value={inputValue}
                />
            </div>
        </div>
    );
}
