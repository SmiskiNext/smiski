'use client';

import { Globe } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { HomeHeader } from './header.tsx';
import { HomeFeatureCards, HomeHero } from './hero.tsx';

export function HomeContainer() {
    const t = useTranslations('home');
    const locale = useLocale();
    const [now, setNow] = useState('');

    useEffect(() => {
        const formatter = new Intl.DateTimeFormat(
            locale === 'vi' ? 'vi-VN' : 'en-US',
            {
                hour: 'numeric',
                minute: '2-digit',
                hour12: true,
                weekday: 'short',
                month: 'short',
                day: 'numeric',
            },
        );

        const updateTime = () => {
            setNow(formatter.format(new Date()));
        };

        updateTime();
        const timer = window.setInterval(updateTime, 60_000);

        return () => window.clearInterval(timer);
    }, [locale]);

    const footerLinks = [
        t('footerAbout'),
        t('footerPrivacy'),
        t('footerTerms'),
        t('footerHelp'),
        t('footerSecurity'),
    ];

    return (
        <main className='min-h-screen bg-surface-pale text-text-darkest'>
            <div className='mx-auto flex min-h-screen w-full max-w-[1600px] flex-col px-5 pb-6 pt-5 sm:px-8 lg:px-10'>
                <HomeHeader locale={locale} now={now} />
                <HomeHero />
                <HomeFeatureCards />

                <footer className='mt-auto flex flex-col gap-5 border-t border-border-muted py-8 text-text-dim lg:flex-row lg:items-center lg:justify-between'>
                    <nav className='flex flex-wrap items-center gap-x-10 gap-y-4 text-lg'>
                        {footerLinks.map((label) => (
                            <Link
                                className='transition-colors hover:text-primary'
                                href={`/${locale}`}
                                key={label}
                            >
                                {label}
                            </Link>
                        ))}
                    </nav>

                    <div className='flex items-center gap-4 text-base'>
                        <Globe className='h-7 w-7' />
                        <p>
                            {t('copyright', { year: new Date().getFullYear() })}
                        </p>
                    </div>
                </footer>
            </div>
        </main>
    );
}
