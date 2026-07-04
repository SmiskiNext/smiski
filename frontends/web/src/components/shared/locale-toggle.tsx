'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useMemo } from 'react';

const SUPPORTED_LOCALES = ['vi', 'en'] as const;

type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

function buildPathForLocale(pathname: string, nextLocale: SupportedLocale) {
    const segments = pathname.split('/').filter(Boolean);
    if (segments.length === 0) {
        return `/${nextLocale}`;
    }
    const [first, ...rest] = segments;
    if (SUPPORTED_LOCALES.includes(first as SupportedLocale)) {
        return `/${[nextLocale, ...rest].join('/')}`;
    }
    return `/${[nextLocale, ...segments].join('/')}`;
}

type LocaleToggleProps = {
    namespace?: string;
};

export function LocaleToggle({
    namespace = 'workspace.common',
}: LocaleToggleProps) {
    const t = useTranslations(namespace);
    const currentLocale = useLocale();
    const pathname = usePathname();

    const targets = useMemo(
        () =>
            SUPPORTED_LOCALES.map((locale) => ({
                locale,
                href: buildPathForLocale(pathname ?? '/', locale),
            })),
        [pathname],
    );

    return (
        // biome-ignore lint/a11y/useSemanticElements: role="group" is correct for a toggle group
        <div
            aria-label={t('languageGroup')}
            className='inline-flex items-center rounded-full border border-border-input bg-surface p-1 shadow-sm'
            role='group'
        >
            {targets.map(({ locale, href }) => {
                const isActive = locale === currentLocale;
                return (
                    <Link
                        aria-pressed={isActive}
                        className={`rounded-full px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.2em] transition-colors ${
                            isActive
                                ? 'bg-primary text-white'
                                : 'text-primary hover:bg-primary-subtle'
                        }`}
                        href={href}
                        key={locale}
                    >
                        {locale}
                    </Link>
                );
            })}
        </div>
    );
}
