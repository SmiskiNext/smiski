'use client';

import { useTheme } from 'next-themes';
import type { ComponentProps } from 'react';
import { Toaster as Sonner } from 'sonner';

type ToasterProps = ComponentProps<typeof Sonner>;

function Toaster({ ...props }: ToasterProps) {
    const { theme = 'system' } = useTheme();

    return (
        <Sonner
            className='toaster group'
            position='top-right'
            theme={theme as ToasterProps['theme']}
            toastOptions={{
                classNames: {
                    toast: 'group toast group-[.toaster]:bg-surface group-[.toaster]:text-text-dark group-[.toaster]:border-border group-[.toaster]:shadow-lg',
                    description: 'group-[.toast]:text-text-secondary',
                    actionButton:
                        'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
                    cancelButton:
                        'group-[.toast]:bg-muted group-[.toast]:text-secondary-foreground',
                },
            }}
            {...props}
        />
    );
}

export { Toaster };
