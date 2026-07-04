'use client';

import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect } from 'react';

const SPLASH_DELAY_MS = 2200;

export function SplashScreen() {
    const router = useRouter();
    const locale = useLocale();
    const t = useTranslations('splash');

    useEffect(() => {
        const timer = window.setTimeout(() => {
            router.replace(`/${locale}/home`);
        }, SPLASH_DELAY_MS);

        return () => window.clearTimeout(timer);
    }, [locale, router]);

    return (
        <main className='relative flex min-h-screen items-center justify-center overflow-hidden bg-[linear-gradient(160deg,_#031525_0%,_#06345d_38%,_#0f7cc8_100%)] px-6 text-white'>
            <div className='absolute inset-0'>
                <div className='absolute left-1/2 top-16 h-64 w-64 -translate-x-1/2 rounded-full bg-cyan-300/20 blur-3xl' />
                <div className='absolute bottom-10 right-[-4rem] h-72 w-72 rounded-full bg-sky-300/20 blur-3xl' />
                <div className='absolute left-[-3rem] top-1/2 h-56 w-56 -translate-y-1/2 rounded-full bg-blue-500/20 blur-3xl' />
            </div>

            <section className='relative flex w-full max-w-xl flex-col items-center text-center'>
                <div className='animate-splash-orbit mb-8 flex h-24 w-24 items-center justify-center rounded-[2rem] border border-white/20 bg-white/10 shadow-[0_20px_60px_-20px_rgba(34,211,238,0.85)] backdrop-blur'>
                    <div className='flex h-14 w-14 items-center justify-center rounded-[1.2rem] bg-white text-xl font-bold tracking-[0.3em] text-sky-700'>
                        ZM
                    </div>
                </div>

                <h1 className='mt-5 animate-splash-rise text-4xl font-semibold tracking-tight sm:text-5xl'>
                    {t('title')}
                </h1>
                <p className='mt-4 max-w-md animate-splash-rise text-base leading-7 text-sky-50/85 [animation-delay:140ms]'>
                    {t('subtitle')}
                </p>

                <div className='mt-10 flex items-center gap-3 rounded-full border border-white/15 bg-white/10 px-5 py-3 backdrop-blur'>
                    <span className='h-2.5 w-2.5 animate-pulse rounded-full bg-cyan-300' />
                    <span className='text-sm font-medium text-sky-50/90'>
                        {t('loading')}
                    </span>
                </div>
            </section>
        </main>
    );
}
