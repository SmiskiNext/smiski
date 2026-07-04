'use client';

import { ShieldCheck } from 'lucide-react';
import Link from 'next/link';

type AuthHeroLoginProps = {
    variant: 'login';
    brand: string;
    brandHref: string;
    eyebrow: string;
    brandHeadline: string;
    heroDescription: string;
};

type AuthHeroRegisterProps = {
    variant: 'register';
    brand: string;
    brandHref: string;
    heroLineOne: string;
    heroLineTwo: string;
    heroLineThree: string;
    heroDescription: string;
    securityCardTitle: string;
    securityCardDescription: string;
};

type AuthHeroProps = AuthHeroLoginProps | AuthHeroRegisterProps;

function LoginPreviewCard() {
    return (
        <div className='relative mt-12 max-w-[700px] rounded-[2rem] border border-white/90 bg-white/85 p-4 shadow-[0_24px_70px_-36px_rgba(15,23,42,0.28)]'>
            <div className='aspect-[1.8] overflow-hidden rounded-[1.5rem] bg-[linear-gradient(135deg,_var(--preview-card-bg-start)_0%,_var(--preview-card-bg-end)_100%)]'>
                <div className='flex h-full flex-col justify-between bg-[radial-gradient(circle_at_top,_rgba(255,255,255,0.85),_rgba(232,235,240,0.25))] p-5'>
                    <div className='grid grid-cols-3 gap-3'>
                        <div className='h-2.5 w-2.5 rounded-full bg-white/80' />
                        <div className='h-2.5 w-2.5 rounded-full bg-white/60' />
                        <div className='h-2.5 w-2.5 rounded-full bg-white/45' />
                    </div>
                    <div className='grid flex-1 grid-cols-[1.2fr_1fr] gap-5 py-5'>
                        <div className='rounded-[1.35rem] bg-[linear-gradient(180deg,_var(--preview-tile-dark-start),_var(--preview-tile-dark-end))]' />
                        <div className='rounded-[1.35rem] bg-[linear-gradient(180deg,_var(--preview-tile-light-start),_var(--preview-tile-light-end))]' />
                    </div>
                    <div className='rounded-[1.2rem] bg-[var(--preview-dark-card)] p-4 text-white'>
                        <div className='flex items-center justify-between'>
                            <div className='flex items-center gap-3'>
                                <div className='h-12 w-12 rounded-full bg-[var(--preview-avatar-gray)]' />
                                <div>
                                    <div className='h-2.5 w-28 rounded-full bg-white/70' />
                                    <div className='mt-2 h-2 w-20 rounded-full bg-white/35' />
                                </div>
                            </div>
                            <div className='flex items-center gap-3'>
                                <span className='h-10 w-10 rounded-full bg-white/15' />
                                <span className='h-10 w-10 rounded-full bg-white/15' />
                                <span className='h-10 w-10 rounded-full bg-white/15' />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function SecurityCard({
    title,
    description,
}: {
    title: string;
    description: string;
}) {
    return (
        <div className='mt-auto hidden max-w-[420px] rounded-[2rem] border border-white/85 bg-white/88 p-7 shadow-[0_24px_70px_-36px_rgba(15,23,42,0.28)] lg:block'>
            <div className='flex items-center gap-4'>
                <div className='flex h-14 w-14 items-center justify-center rounded-full bg-icon-security-bg text-icon-security-color'>
                    <ShieldCheck className='h-7 w-7' />
                </div>
                <div>
                    <p className='text-2xl font-semibold text-text-primary'>
                        {title}
                    </p>
                    <p className='mt-1 text-base text-text-muted'>
                        {description}
                    </p>
                </div>
            </div>
        </div>
    );
}

export function AuthHero(props: AuthHeroProps) {
    const { variant, brand, brandHref } = props;

    return (
        <div
            className={`relative overflow-hidden ${
                variant === 'login'
                    ? 'bg-[linear-gradient(145deg,_var(--surface)_0%,_var(--surface-hero-start)_60%,_var(--surface-hero-end)_100%)]'
                    : 'bg-[linear-gradient(145deg,_rgba(255,255,255,0.82)_0%,_var(--surface-hero-register-start)_55%,_var(--surface-hero-register-end)_100%)]'
            }`}
        >
            <div className='absolute inset-0'>
                <div className='absolute left-[-8%] top-[8%] h-72 w-72 rounded-full bg-[var(--orb-blue-1)] blur-3xl' />
                <div className='absolute bottom-[-10%] right-[-6%] h-80 w-80 rounded-full bg-[var(--orb-blue-2)] blur-3xl' />
                {variant === 'register' ? (
                    <div className='absolute inset-0 bg-[linear-gradient(90deg,rgba(255,255,255,0.55),rgba(255,255,255,0.18))]' />
                ) : null}
            </div>

            <div className='relative flex h-full flex-col px-8 py-8 sm:px-12 sm:py-12 lg:px-16'>
                <Link
                    className={`self-start text-[2.25rem] font-semibold tracking-tight ${variant === 'login' ? 'text-text-primary' : 'text-primary'}`}
                    href={brandHref}
                >
                    {brand}
                </Link>

                <div className='mt-14 max-w-[620px] lg:mt-20'>
                    {variant === 'login' ? (
                        <>
                            <p className='text-sm font-semibold uppercase tracking-[0.28em] text-brand-blue'>
                                {props.eyebrow}
                            </p>
                            <h1 className='mt-7 text-6xl font-semibold leading-[0.95] tracking-tight text-text-dark sm:text-7xl'>
                                {props.brandHeadline}
                            </h1>
                        </>
                    ) : (
                        <h1 className='text-5xl font-semibold leading-[1.02] tracking-tight text-text-dark sm:text-6xl'>
                            {props.heroLineOne}
                            <br />
                            <span className='text-primary'>
                                {props.heroLineTwo}
                            </span>{' '}
                            {props.heroLineThree}
                        </h1>
                    )}
                    <p className='mt-7 max-w-[520px] text-2xl leading-[1.45] text-text-secondary sm:text-[1.15rem] sm:leading-10'>
                        {props.heroDescription}
                    </p>
                </div>

                {variant === 'login' ? (
                    <LoginPreviewCard />
                ) : (
                    <SecurityCard
                        description={props.securityCardDescription}
                        title={props.securityCardTitle}
                    />
                )}
            </div>
        </div>
    );
}
