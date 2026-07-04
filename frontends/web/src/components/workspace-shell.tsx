'use client';

import { useLocale, useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { AppHeader } from '@/components/shared/app-header.tsx';

type WorkspaceShellProps = {
    activeTab: 'home' | 'schedule' | 'profile' | 'history';
    rightMode?: 'compact' | 'search';
    children: ReactNode;
};

export function WorkspaceShell({
    activeTab,
    rightMode = 'compact',
    children,
}: WorkspaceShellProps) {
    const locale = useLocale();
    const t = useTranslations('workspace.common');
    const basePath = `/${locale}/workspace`;

    const navItems = [
        { id: 'home', label: t('navHome'), href: basePath },
        {
            id: 'schedule',
            label: t('navSchedule'),
            href: `${basePath}/schedule`,
        },
        {
            id: 'history',
            label: t('navHistory'),
            href: `${basePath}/history`,
        },
    ] as const;

    return (
        <main className='min-h-screen bg-surface-alt text-text-primary'>
            <AppHeader
                activeNavId={activeTab}
                brand={t('brand')}
                brandHref={basePath}
                helpLabel={t('help')}
                navItems={[...navItems]}
                notificationsLabel={t('notifications')}
                profileLabel={t('profile')}
                rightMode={rightMode}
                searchPlaceholder={t('searchPlaceholder')}
                variant='workspace'
            />

            <div className='mx-auto max-w-[1600px] px-6 py-8 sm:px-8 lg:px-10 lg:py-10'>
                {children}
            </div>
        </main>
    );
}
