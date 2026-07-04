'use client';

import { Monitor, Shield, Users, Video } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { JoinForm } from './join-form.tsx';

const PREVIEW_TILES = [
    {
        id: 'gray-top-left',
        className:
            'bg-[var(--preview-tile-gray)] text-[var(--preview-tile-gray-text)]',
    },
    {
        id: 'blue-light',
        className: 'bg-[var(--preview-tile-blue-light)] text-primary',
    },
    {
        id: 'blue-lighter',
        className: 'bg-[var(--preview-tile-blue-lighter)] text-primary',
    },
    {
        id: 'gray-bottom-right',
        className:
            'bg-[var(--preview-tile-gray)] text-[var(--preview-tile-gray-text)]',
    },
];

export function HomeHero() {
    const t = useTranslations('home');
    const locale = useLocale();
    const [joinCode, setJoinCode] = useState('');

    return (
        <section className='grid flex-1 items-start gap-10 pt-8 md:grid-cols-[minmax(0,1fr)_minmax(360px,540px)] md:gap-10 md:pt-10 lg:grid-cols-[minmax(0,1fr)_minmax(460px,690px)] lg:gap-14 lg:pt-16'>
            <div className='max-w-190'>
                <h1 className='max-w-180 text-4xl font-medium leading-[1.06] tracking-tight text-text-darkest sm:text-5xl md:text-6xl lg:text-[4.8rem]'>
                    {t('headline')}
                </h1>
                <p className='mt-6 max-w-160 text-lg leading-9 text-text-slate sm:text-xl sm:leading-10 md:text-[1.08rem] md:leading-9'>
                    {t('description')}
                </p>

                <JoinForm
                    joinCode={joinCode}
                    locale={locale}
                    onJoinCodeChange={setJoinCode}
                />

                <div className='mt-10 h-px w-full max-w-190 bg-border-muted' />

                <Link
                    className='mt-10 inline-flex text-lg text-primary transition-colors hover:text-primary-hover'
                    href={`/${locale}`}
                >
                    {t('learnMore')}
                </Link>
            </div>

            <div className='rounded-[1.9rem] border border-border-card bg-surface-card p-6 shadow-[0_24px_60px_-34px_rgba(15,23,42,0.28)] sm:p-8'>
                <div className='grid grid-cols-2 gap-3 sm:gap-5'>
                    {PREVIEW_TILES.map(({ id, className }, index) => (
                        <div
                            className={`flex aspect-[1.35] items-center justify-center rounded-[1.1rem] ${className}`}
                            key={id}
                        >
                            <Video className='h-8 w-8 sm:h-10 sm:w-10' />
                            <span className='sr-only'>{index + 1}</span>
                        </div>
                    ))}
                </div>

                <p className='px-2 pb-3 pt-6 text-center text-[0.95rem] text-text-slate sm:px-4 sm:pt-10 sm:text-[1.05rem] md:text-[1.12rem]'>
                    {t('previewCaption')}
                </p>
            </div>
        </section>
    );
}

type FeatureCard = {
    title: string;
    description: string;
    Icon: React.ComponentType<{ className?: string }>;
};

export function HomeFeatureCards() {
    const t = useTranslations('home');

    const featureCards: FeatureCard[] = [
        {
            title: t('featureSecurityTitle'),
            description: t('featureSecurityBody'),
            Icon: Shield,
        },
        {
            title: t('featureDeviceTitle'),
            description: t('featureDeviceBody'),
            Icon: Monitor,
        },
        {
            title: t('featureScaleTitle'),
            description: t('featureScaleBody'),
            Icon: Users,
        },
    ];

    return (
        <section className='grid gap-14 pb-20 pt-20 text-center md:grid-cols-3 md:gap-12 lg:pt-28'>
            {featureCards.map(({ title, description, Icon }) => (
                <article className='mx-auto max-w-sm' key={title}>
                    <span className='mx-auto inline-flex h-14 w-14 items-center justify-center rounded-full bg-primary-subtle text-primary'>
                        <Icon className='h-8 w-8' />
                    </span>
                    <h2 className='mt-7 text-[2rem] font-normal tracking-tight text-text-darkest'>
                        {title}
                    </h2>
                    <p className='mt-6 text-[1.02rem] leading-9 text-text-slate'>
                        {description}
                    </p>
                </article>
            ))}
        </section>
    );
}
