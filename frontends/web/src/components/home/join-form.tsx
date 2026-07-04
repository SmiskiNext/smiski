'use client';

import { Keyboard, Plus } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

type JoinFormProps = {
    locale: string;
    joinCode: string;
    onJoinCodeChange: (value: string) => void;
};

export function JoinForm({
    locale,
    joinCode,
    onJoinCodeChange,
}: JoinFormProps) {
    const t = useTranslations('home');
    const hasJoinValue = joinCode.trim().length > 0;

    return (
        <div className='mt-10 flex flex-col gap-4 md:flex-row md:flex-wrap md:items-center'>
            <Link
                className='group inline-flex h-14 items-center justify-center gap-3 rounded-2xl bg-[linear-gradient(135deg,_var(--primary)_0%,_var(--primary-deep)_100%)] px-5 text-base font-medium text-white shadow-[0_22px_46px_-24px_rgba(26,115,232,0.95)] ring-1 ring-primary/20 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_28px_52px_-24px_rgba(26,115,232,0.95)] sm:h-16 sm:px-6 sm:text-[1.12rem]'
                href={`/${locale}/workspace/green-room`}
            >
                <span className='flex h-9 w-9 items-center justify-center rounded-xl bg-white/18 transition-colors group-hover:bg-white/24'>
                    <Plus className='h-5 w-5' />
                </span>
                {t('newMeeting')}
            </Link>

            <div className='flex h-14 w-full min-w-0 items-center gap-3 rounded-2xl border border-border-input bg-surface px-4 text-text-dim shadow-[0_18px_40px_-35px_rgba(15,23,42,0.45)] sm:h-16 sm:px-5 md:max-w-[420px] md:flex-1 lg:max-w-[520px]'>
                <Keyboard
                    aria-hidden='true'
                    className='h-5 w-5 shrink-0 text-text-slate'
                />
                <input
                    className='h-full w-full min-w-0 bg-transparent text-base text-text-darkest outline-none placeholder:text-text-dim sm:text-[1.08rem]'
                    onChange={(event) => onJoinCodeChange(event.target.value)}
                    placeholder={t('joinPlaceholder')}
                    type='text'
                    value={joinCode}
                />
            </div>

            <Link
                aria-disabled={!hasJoinValue}
                tabIndex={hasJoinValue ? 0 : -1}
                className={`inline-flex h-14 items-center justify-center rounded-2xl px-5 text-base font-medium transition-all sm:h-16 sm:text-[1.12rem] ${
                    hasJoinValue
                        ? 'cursor-pointer bg-primary-muted text-primary shadow-[0_16px_30px_-24px_rgba(26,115,232,0.85)] hover:bg-primary hover:text-white'
                        : 'pointer-events-none text-text-disabled'
                }`}
                href={`/${locale}/join/${joinCode.trim()}`}
            >
                {t('join')}
            </Link>
        </div>
    );
}
