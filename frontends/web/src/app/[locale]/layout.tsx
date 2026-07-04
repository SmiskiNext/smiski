import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { hasLocale, NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { ApiClientProvider } from '@/components/api-client-provider.tsx';
import { routing } from '@/i18n/request.ts';

export const metadata: Metadata = {
    title: 'Zero Meeting System',
    description: 'A modern meeting management platform',
};

export default async function RootLayout({
    children,
    params,
}: LayoutProps<'/[locale]'>) {
    const { locale } = await params;
    if (!hasLocale(routing.locales, locale)) {
        notFound();
    }

    const messages = await getMessages({ locale });

    return (
        <NextIntlClientProvider locale={locale} messages={messages}>
            <ApiClientProvider>{children}</ApiClientProvider>
        </NextIntlClientProvider>
    );
}
