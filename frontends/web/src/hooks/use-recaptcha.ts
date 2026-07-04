'use client';

import { useCallback, useState } from 'react';

type RecaptchaRenderOptions = {
    sitekey: string;
    callback: (token: string) => void;
    'expired-callback'?: () => void;
    'error-callback'?: () => void;
};

type Grecaptcha = {
    ready: (callback: () => void) => void;
    render: (container: HTMLElement, options: RecaptchaRenderOptions) => number;
    reset: (widgetId?: number) => void;
};

declare global {
    interface Window {
        grecaptcha?: Grecaptcha;
        onRecaptchaLoadCallback?: () => void;
    }
}

type UseRecaptchaReturn = {
    render: (container: HTMLElement) => Promise<void>;
    token: string | null;
    isReady: boolean;
    error: string | null;
    reset: () => void;
};

const RECAPTCHA_SCRIPT_ID = 'google-recaptcha-script';
const RECAPTCHA_LOAD_CALLBACK = 'onRecaptchaLoadCallback';
let scriptLoadPromise: Promise<void> | null = null;

function getRecaptchaScriptSrc() {
    return `https://www.google.com/recaptcha/api.js?onload=${RECAPTCHA_LOAD_CALLBACK}&render=explicit`;
}

function loadRecaptchaScript(): Promise<void> {
    if (typeof window === 'undefined') {
        return Promise.reject(new Error('reCAPTCHA is unavailable'));
    }

    if (window.grecaptcha) {
        return Promise.resolve();
    }

    if (scriptLoadPromise) {
        return scriptLoadPromise;
    }

    scriptLoadPromise = new Promise((resolve, reject) => {
        window.onRecaptchaLoadCallback = () => resolve();

        const existingScript = document.getElementById(RECAPTCHA_SCRIPT_ID);
        if (existingScript) {
            existingScript.addEventListener(
                'error',
                () => reject(new Error('Unable to load reCAPTCHA')),
                {
                    once: true,
                },
            );
            return;
        }

        const script = document.createElement('script');
        script.id = RECAPTCHA_SCRIPT_ID;
        script.src = getRecaptchaScriptSrc();
        script.async = true;
        script.defer = true;
        script.onerror = () => reject(new Error('Unable to load reCAPTCHA'));
        document.head.appendChild(script);
    });

    return scriptLoadPromise;
}

function waitForRecaptchaReady(): Promise<Grecaptcha> {
    return new Promise((resolve, reject) => {
        const grecaptcha = window.grecaptcha;

        if (!grecaptcha) {
            reject(new Error('reCAPTCHA is unavailable'));
            return;
        }

        grecaptcha.ready(() => resolve(grecaptcha));
    });
}

export function useRecaptcha(
    siteKey = process.env.NEXT_PUBLIC_RECAPTCHA_SITE_KEY ?? '',
): UseRecaptchaReturn {
    const [token, setToken] = useState<string | null>(null);
    const [widgetId, setWidgetId] = useState<number | null>(null);
    const [isReady, setIsReady] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const ensureReady = useCallback(async () => {
        if (!siteKey) {
            throw new Error('reCAPTCHA site key is not configured');
        }

        setError(null);
        await loadRecaptchaScript();
        const grecaptcha = await waitForRecaptchaReady();
        setIsReady(true);
        return grecaptcha;
    }, [siteKey]);

    const render = useCallback(
        async (container: HTMLElement) => {
            try {
                const grecaptcha = await ensureReady();

                if (widgetId !== null) {
                    grecaptcha.reset(widgetId);
                    setToken(null);
                    return;
                }

                const renderedWidgetId = grecaptcha.render(container, {
                    sitekey: siteKey,
                    callback: setToken,
                    'expired-callback': () => setToken(null),
                    'error-callback': () => {
                        setToken(null);
                        setError('reCAPTCHA verification failed');
                    },
                });

                setWidgetId(renderedWidgetId);
            } catch (error) {
                setError(
                    error instanceof Error
                        ? error.message
                        : 'reCAPTCHA is unavailable',
                );
            }
        },
        [ensureReady, siteKey, widgetId],
    );

    const reset = useCallback(() => {
        if (window.grecaptcha && widgetId !== null) {
            window.grecaptcha.reset(widgetId);
        }

        setToken(null);
    }, [widgetId]);

    return { render, token, isReady, error, reset };
}
