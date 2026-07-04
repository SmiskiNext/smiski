'use client';

import { Monitor, Moon, Sun } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu.tsx';

const THEME_OPTIONS = [
    { value: 'light', icon: Sun, labelKey: 'themeLight' },
    { value: 'dark', icon: Moon, labelKey: 'themeDark' },
    { value: 'system', icon: Monitor, labelKey: 'themeSystem' },
] as const;

type ThemeToggleProps = {
    namespace?: string;
};

export function ThemeToggle({
    namespace = 'workspace.common',
}: ThemeToggleProps) {
    const t = useTranslations(namespace);
    const { theme, resolvedTheme, setTheme } = useTheme();
    const [mounted, setMounted] = useState(false);

    useEffect(() => {
        setMounted(true);
    }, []);

    const activeTheme = theme ?? 'system';
    const ActiveIcon = mounted ? (resolvedTheme === 'dark' ? Moon : Sun) : Sun;

    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                aria-label={t('themeToggle')}
                className='inline-flex h-10 w-10 items-center justify-center rounded-full border border-border-input bg-surface text-text-muted shadow-sm transition-colors hover:bg-primary-subtle hover:text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2'
                type='button'
            >
                <ActiveIcon className='h-5 w-5' />
            </DropdownMenuTrigger>
            <DropdownMenuContent align='end' className='min-w-[10rem]'>
                {THEME_OPTIONS.map(({ value, icon: Icon, labelKey }) => {
                    const isActive = activeTheme === value;
                    return (
                        <DropdownMenuItem
                            className={`gap-2 ${
                                isActive ? 'bg-primary-subtle text-primary' : ''
                            }`}
                            key={value}
                            onSelect={() => setTheme(value)}
                        >
                            <Icon className='h-4 w-4' />
                            <span>{t(labelKey)}</span>
                        </DropdownMenuItem>
                    );
                })}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
