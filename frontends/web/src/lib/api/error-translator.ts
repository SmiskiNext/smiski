import enMessages from '@/messages/en.json';
import viMessages from '@/messages/vi.json';
import type { ErrorTranslator } from './types.ts';

type SupportedLocale = 'en' | 'vi';

const MESSAGES_MAP: Record<SupportedLocale, Record<string, string>> = {
    en: enMessages.errors as Record<string, string>,
    vi: viMessages.errors as Record<string, string>,
};

function getLocaleFromCookie(): SupportedLocale {
    if (typeof document === 'undefined') {
        return 'en';
    }
    const match = document.cookie
        .split('; ')
        .find((row) => row.startsWith('NEXT_LOCALE='));
    const locale = match?.split('=')[1];
    if (locale === 'vi') {
        return 'vi';
    }
    return 'en';
}

/**
 * Static error translator for the web frontend.
 *
 * Resolves API error codes to locale-specific messages using
 * statically imported JSON message files. Reads the active locale
 * from the NEXT_LOCALE cookie at call time (no React hooks required).
 *
 * Falls back to {@link defaultMessage} when the error code has no
 * matching key in the messages map.
 */
export const webErrorTranslator: ErrorTranslator = (
    code: string,
    defaultMessage: string,
): string => {
    const locale = getLocaleFromCookie();
    const messages = MESSAGES_MAP[locale];
    return messages[code] ?? defaultMessage;
};

export default webErrorTranslator;
