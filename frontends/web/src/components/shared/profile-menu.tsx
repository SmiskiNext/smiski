'use client';

import { History, LogOut, User } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Avatar, AvatarFallback } from '@/components/ui/avatar.tsx';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu.tsx';
import { useLogout } from '@/hooks/use-logout.ts';

type ProfileMenuProps = {
    label: string;
};

export function ProfileMenu({ label }: ProfileMenuProps) {
    const t = useTranslations('workspace.common');
    const locale = useLocale();
    const { logoutState, logout } = useLogout();
    const isLoggingOut = logoutState === 'LOGGING_OUT';
    const basePath = `/${locale}/workspace`;

    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                aria-label={label}
                className='rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2'
                type='button'
            >
                <Avatar className='h-12 w-12 shadow-[0_14px_28px_-18px_rgba(26,115,232,0.85)]'>
                    <AvatarFallback className='bg-[linear-gradient(135deg,_var(--avatar-gradient-navy-start)_0%,_var(--avatar-gradient-navy-end)_100%)] text-white'>
                        <User className='h-6 w-6' />
                    </AvatarFallback>
                </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent align='end' className='min-w-[14rem]'>
                <DropdownMenuItem asChild className='gap-3 py-2.5'>
                    <Link href={`${basePath}/profile`}>
                        <User className='h-4 w-4 text-text-muted' />
                        <span>{t('profileMenuProfile')}</span>
                    </Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild className='gap-3 py-2.5'>
                    <Link href={`${basePath}/history`}>
                        <History className='h-4 w-4 text-text-muted' />
                        <span>{t('profileMenuHistory')}</span>
                    </Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                    className='gap-3 py-2.5 text-error focus:text-error'
                    disabled={isLoggingOut}
                    onSelect={(event) => {
                        event.preventDefault();
                        logout(locale, t('profileMenuLogoutError'));
                    }}
                >
                    <LogOut className='h-4 w-4' />
                    <span>
                        {isLoggingOut
                            ? t('profileMenuLoggingOut')
                            : t('profileMenuLogout')}
                    </span>
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
