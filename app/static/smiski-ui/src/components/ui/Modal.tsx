import { type ReactNode, useEffect, useId } from 'react';
import { Button } from './Button';
import { Icon } from './Icon';

export interface ModalProps {
    title: string;
    description?: string;
    children: ReactNode;
    footer?: ReactNode;
    onClose: () => void;
    size?: 'sm' | 'md' | 'lg' | 'xl';
    /**
     * 'overlay' (default) draws its own backdrop + centers itself in the
     * viewport — for use inside a normal page/panel. 'embedded' skips both and
     * fills its container instead, for when a Forge platform Modal
     * (`@forge/bridge`'s `Modal`) already provides the backdrop, sizing and
     * close affordance — drawing our own on top produced a stray dim/gray
     * layer around the card.
     */
    chrome?: 'overlay' | 'embedded';
}

const sizes = {
    sm: 'max-w-md',
    md: 'max-w-xl',
    lg: 'max-w-2xl',
    xl: 'max-w-3xl',
};

export function Modal({
    title,
    description,
    children,
    footer,
    onClose,
    size = 'md',
    chrome = 'overlay',
}: ModalProps) {
    const titleId = useId();

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) =>
            event.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [onClose]);

    const card = (
        <section
            role='dialog'
            aria-modal='true'
            aria-labelledby={titleId}
            className={
                chrome === 'embedded'
                    ? 'fixed inset-0 flex flex-col overflow-hidden bg-[var(--surface)]'
                    : `relative flex max-h-[calc(100vh-2rem)] w-full flex-col overflow-hidden rounded-xl border bg-[var(--surface)] shadow-panel ${sizes[size]}`
            }
        >
            <header className='flex items-start justify-between gap-4 border-b px-5 py-4 sm:px-6'>
                <div>
                    <h2
                        id={titleId}
                        className='text-lg font-bold tracking-tight text-[var(--text)]'
                    >
                        {title}
                    </h2>
                    {description && (
                        <p className='mt-1 text-sm text-[var(--text-muted)]'>
                            {description}
                        </p>
                    )}
                </div>
                <Button
                    size='icon'
                    variant='ghost'
                    aria-label='Close'
                    onClick={onClose}
                >
                    <Icon name='x' />
                </Button>
            </header>
            <div className='scrollbar-subtle flex-1 overflow-y-auto px-5 py-5 sm:px-6'>
                {children}
            </div>
            {footer && (
                <footer className='flex flex-wrap justify-end gap-2 border-t bg-[var(--surface-soft)] px-5 py-4 sm:px-6'>
                    {footer}
                </footer>
            )}
        </section>
    );

    if (chrome === 'embedded') return card;

    return (
        <div
            className='fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6'
            role='presentation'
        >
            <button
                type='button'
                className='absolute inset-0 cursor-default bg-slate-950/55 backdrop-blur-[2px]'
                aria-label='Close dialog'
                onClick={onClose}
            />
            <div className={`relative w-full ${sizes[size]}`}>{card}</div>
        </div>
    );
}
