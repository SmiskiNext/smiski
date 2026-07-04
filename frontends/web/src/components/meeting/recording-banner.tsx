'use client';

import { X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useRef } from 'react';

type RecordingBannerType = 'started' | 'stopped';

type RecordingBannerProps = {
    type: RecordingBannerType | null;
    onDismiss: () => void;
};

const STARTED_AUTO_DISMISS_MS = 8_000;
const STOPPED_AUTO_DISMISS_MS = 5_000;

/**
 * Inline banner shown below the meeting header on recording start and stop
 * transitions. Auto-dismissed after variant-specific delays. Uses assertive
 * aria-live for started and polite for stopped to match accessibility intent.
 */
export function RecordingBanner({ type, onDismiss }: RecordingBannerProps) {
    const t = useTranslations('meetingRoom');
    const dismissRef = useRef(onDismiss);
    dismissRef.current = onDismiss;

    useEffect(() => {
        if (!type) return;

        const delay =
            type === 'started'
                ? STARTED_AUTO_DISMISS_MS
                : STOPPED_AUTO_DISMISS_MS;

        const timer = setTimeout(() => {
            dismissRef.current();
        }, delay);

        return () => {
            clearTimeout(timer);
        };
    }, [type]);

    if (!type) return null;

    const isStarted = type === 'started';

    return (
        <div
            aria-live={isStarted ? 'assertive' : 'polite'}
            className={`flex shrink-0 items-center justify-between px-6 py-2 ${
                isStarted ? 'bg-error/10' : 'bg-surface-input'
            }`}
            role={isStarted ? 'alert' : 'status'}
        >
            <div className='flex items-center gap-2'>
                {isStarted && (
                    <span className='inline-flex h-2.5 w-2.5 rounded-full bg-error' />
                )}
                <span className='text-sm font-medium'>
                    {isStarted
                        ? t('recordingStartedBanner')
                        : t('recordingStoppedBanner')}
                </span>
            </div>
            <button
                aria-label={t('dismissRecordingBanner')}
                className='ml-4 rounded p-1 opacity-60 transition-opacity hover:opacity-100'
                onClick={onDismiss}
                type='button'
            >
                <X className='h-4 w-4' />
            </button>
        </div>
    );
}
