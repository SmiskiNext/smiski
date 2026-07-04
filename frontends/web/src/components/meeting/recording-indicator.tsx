'use client';

import { useTranslations } from 'next-intl';

type RecordingIndicatorProps = {
    isVisible: boolean;
};

/**
 * Pulsing red dot with "REC" label shown in the meeting header to all
 * participants when recording is active. Uses motion-safe ping animation
 * for the ring and aria-live for screen reader accessibility.
 */
export function RecordingIndicator({ isVisible }: RecordingIndicatorProps) {
    const t = useTranslations('meetingRoom');

    if (!isVisible) return null;

    return (
        <div
            aria-label={t('recordingActive')}
            aria-live='polite'
            className='flex items-center gap-1.5'
            role='status'
        >
            <div className='relative flex h-5 w-5 items-center justify-center'>
                <span className='absolute inline-flex h-full w-full motion-safe:animate-ping rounded-full bg-error/30 opacity-75' />
                <span className='relative inline-flex h-2.5 w-2.5 rounded-full bg-error' />
            </div>
            <span className='text-xs font-semibold tracking-wider text-error'>
                {t('recordingActive')}
            </span>
        </div>
    );
}
