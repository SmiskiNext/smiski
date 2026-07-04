import { hasLocale } from 'next-intl';
import { defineRouting } from 'next-intl/routing';
import { getRequestConfig } from 'next-intl/server';

export const routing = defineRouting({
    locales: ['en', 'vi'],
    defaultLocale: 'en',
    pathnames: {},
});

export default getRequestConfig(async ({ requestLocale }) => {
    const requested = await requestLocale;
    const locale = hasLocale(routing.locales, requested)
        ? requested
        : routing.defaultLocale;

    return {
        locale,
        messages: (await import(`../messages/${locale}.json`)).default,
    };
});
