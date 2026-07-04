'use client';

import { X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import {
    useCallback,
    useEffect,
    useId,
    useLayoutEffect,
    useRef,
    useState,
} from 'react';
import { Button } from '@/components/ui/button.tsx';

const FOCUSABLE_SELECTOR = [
    'button:not([disabled])',
    '[href]',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
].join(', ');

type RecordingPlayerProps = {
    url: string | null;
    open: boolean;
    onClose: () => void;
};

function hasPlayableUrl(url: string | null): boolean {
    return Boolean(url?.trim());
}

export function RecordingPlayer({ url, open, onClose }: RecordingPlayerProps) {
    const t = useTranslations('workspace.history');
    const titleId = useId();
    const descriptionId = useId();
    const overlayRef = useRef<HTMLDivElement | null>(null);
    const closeButtonRef = useRef<HTMLButtonElement | null>(null);
    const videoRef = useRef<HTMLVideoElement | null>(null);
    const triggerRef = useRef<HTMLElement | null>(null);
    const [error, setError] = useState(!hasPlayableUrl(url));
    const [playerKey, setPlayerKey] = useState(0);

    const stopPlayback = useCallback(() => {
        const video = videoRef.current;
        if (!video) return;
        video.pause();
        video.removeAttribute('src');
        video.load();
    }, []);

    const handleClose = useCallback(() => {
        stopPlayback();
        onClose();
    }, [onClose, stopPlayback]);

    useEffect(() => {
        if (!open) return;
        triggerRef.current = document.activeElement as HTMLElement | null;
    }, [open]);

    useEffect(() => {
        if (open) {
            setError(!hasPlayableUrl(url));
            setPlayerKey(0);
        }
    }, [open, url]);

    useLayoutEffect(() => {
        if (!open) return;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        closeButtonRef.current?.focus();

        return () => {
            document.body.style.overflow = previousOverflow;
        };
    }, [open]);

    useEffect(() => {
        if (open) return;
        stopPlayback();
        triggerRef.current?.focus();
    }, [open, stopPlayback]);

    useEffect(() => {
        if (!open) return;

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === 'Escape') {
                event.preventDefault();
                handleClose();
                return;
            }

            if (event.key !== 'Tab') return;

            const container = overlayRef.current;
            if (!container) return;
            const focusableElements = Array.from(
                container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
            );
            if (focusableElements.length === 0) {
                event.preventDefault();
                return;
            }

            const firstElement = focusableElements[0];
            const lastElement = focusableElements[focusableElements.length - 1];
            const activeElement = document.activeElement;

            if (event.shiftKey && activeElement === firstElement) {
                event.preventDefault();
                lastElement.focus();
                return;
            }

            if (!event.shiftKey && activeElement === lastElement) {
                event.preventDefault();
                firstElement.focus();
            }
        }

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [open, handleClose]);

    if (!open) return null;

    return (
        <div
            aria-describedby={descriptionId}
            aria-labelledby={titleId}
            aria-modal='true'
            className='fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4'
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    handleClose();
                }
            }}
            ref={overlayRef}
            role='dialog'
        >
            <div className='relative flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-3xl border border-border bg-surface shadow-card-hover'>
                <div className='flex items-center justify-between border-b border-border px-6 py-4'>
                    <div>
                        <h2
                            className='text-lg font-semibold text-text-darkest'
                            id={titleId}
                        >
                            {t('recordingsSectionTitle', { count: 1 })}
                        </h2>
                        <p
                            className='text-sm text-text-muted'
                            id={descriptionId}
                        >
                            {error
                                ? t('playerErrorDescription')
                                : t('playerClose')}
                        </p>
                    </div>
                    <Button
                        aria-label={t('playerClose')}
                        onClick={handleClose}
                        ref={closeButtonRef}
                        size='icon'
                        type='button'
                        variant='ghost'
                    >
                        <X />
                    </Button>
                </div>

                <div className='flex min-h-[320px] items-center justify-center bg-black p-4 sm:p-6'>
                    {error ? (
                        <div className='flex max-w-md flex-col items-center text-center'>
                            <h3 className='text-xl font-semibold text-white'>
                                {t('playerErrorTitle')}
                            </h3>
                            <p className='mt-2 text-sm text-white/80'>
                                {hasPlayableUrl(url)
                                    ? t('playerErrorDescription')
                                    : t('playerMissingUrl')}
                            </p>
                            <Button
                                className='mt-6'
                                onClick={() => {
                                    setError(false);
                                    setPlayerKey((current) => current + 1);
                                }}
                                type='button'
                                variant='secondary'
                            >
                                {t('playerRetry')}
                            </Button>
                        </div>
                    ) : (
                        <video
                            className='max-h-[70vh] w-full rounded-2xl bg-black'
                            controls
                            key={playerKey}
                            onError={() => setError(true)}
                            preload='metadata'
                            ref={videoRef}
                            src={url ?? undefined}
                        >
                            <track kind='captions' />
                        </video>
                    )}
                </div>
            </div>
        </div>
    );
}
