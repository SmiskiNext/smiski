'use client';

import { HelpCircle, MessageSquare, Settings } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { Logo } from '@/components/shared/logo.tsx';
import { Button } from '@/components/ui/button.tsx';

type HomeHeaderProps = {
    now: string;
    locale: string;
};

export function HomeHeader({ now, locale }: HomeHeaderProps) {
    const t = useTranslations('home');

    return (
        <header className='flex flex-col gap-6 md:flex-row md:flex-wrap md:items-center md:justify-between md:gap-4 lg:gap-6'>
            <Link
                className='inline-flex items-center gap-3 self-start text-[22px] font-medium tracking-tight text-text-darkest'
                href={`/${locale}/home`}
            >
                <span className='flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-white shadow-[0_10px_30px_-18px_rgba(26,115,232,0.9)]'>
                    <Logo className='h-5 w-5 text-white' decorative />
                </span>
                <span>{t('brand')}</span>
            </Link>

            <div className='flex flex-col gap-4 sm:flex-row sm:flex-wrap sm:items-center md:gap-5 lg:gap-7'>
                <div className='flex flex-wrap items-center gap-3 text-sm text-text-slate md:gap-4 lg:gap-5'>
                    <span className='hidden lg:inline'>{now}</span>
                    {/* biome-ignore lint/a11y/useSemanticElements: role="group" is correct for a non-form toggle group */}
                    <div
                        aria-label={t('localeGroup')}
                        className='inline-flex items-center rounded-full border border-border-input bg-surface p-1 shadow-sm'
                        role='group'
                    >
                        <Link
                            aria-pressed={locale === 'vi'}
                            className={`rounded-full px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.2em] transition-colors ${
                                locale === 'vi'
                                    ? 'bg-primary text-white'
                                    : 'text-primary hover:bg-primary-subtle'
                            }`}
                            href='/vi/home'
                        >
                            VI
                        </Link>
                        <Link
                            aria-pressed={locale === 'en'}
                            className={`rounded-full px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.2em] transition-colors ${
                                locale === 'en'
                                    ? 'bg-primary text-white'
                                    : 'text-primary hover:bg-primary-subtle'
                            }`}
                            href='/en/home'
                        >
                            EN
                        </Link>
                    </div>
                    <Button
                        aria-label={t('help')}
                        className='rounded-full text-text-dim hover:bg-primary-subtle hover:text-primary'
                        size='icon'
                        type='button'
                        variant='ghost'
                    >
                        <HelpCircle className='h-5 w-5' />
                    </Button>
                    <Button
                        aria-label={t('messages')}
                        className='rounded-full text-text-dim hover:bg-primary-subtle hover:text-primary'
                        size='icon'
                        type='button'
                        variant='ghost'
                    >
                        <MessageSquare className='h-5 w-5' />
                    </Button>
                    <Button
                        aria-label={t('settings')}
                        className='rounded-full text-text-dim hover:bg-primary-subtle hover:text-primary'
                        size='icon'
                        type='button'
                        variant='ghost'
                    >
                        <Settings className='h-5 w-5' />
                    </Button>
                </div>

                <div className='flex items-center gap-3'>
                    <Link
                        className='inline-flex items-center justify-center rounded-full px-4 py-2.5 text-base font-medium text-primary transition-colors hover:bg-primary-subtle'
                        href={`/${locale}/login`}
                    >
                        {t('login')}
                    </Link>
                    <Link
                        className='inline-flex items-center justify-center rounded-2xl bg-primary px-6 py-3 text-base font-medium text-white shadow-[0_16px_32px_-18px_rgba(26,115,232,1)] transition-transform hover:-translate-y-0.5 hover:bg-primary-hover'
                        href={`/${locale}/register`}
                    >
                        {t('register')}
                    </Link>
                </div>
            </div>
        </header>
    );
}
