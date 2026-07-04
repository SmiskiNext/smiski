'use client';

import { Bell, HelpCircle, Search, Settings, User } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { Avatar, AvatarFallback } from '@/components/ui/avatar.tsx';
import { LocaleToggle } from './locale-toggle.tsx';
import { Logo } from './logo.tsx';
import { ProfileMenu } from './profile-menu.tsx';
import { ThemeToggle } from './theme-toggle.tsx';

type NavItem = {
    id: string;
    label: string;
    href: string;
};

type WorkspaceHeaderProps = {
    variant: 'workspace';
    brand: string;
    brandHref: string;
    navItems: NavItem[];
    activeNavId: string;
    rightMode?: 'compact' | 'search';
    helpLabel: string;
    notificationsLabel: string;
    searchPlaceholder: string;
    profileLabel: string;
};

type GreenRoomHeaderProps = {
    variant: 'green-room';
    brand: string;
    brandHref: string;
    helpLabel: string;
    settingsLabel: string;
    profileLabel: string;
};

type AppHeaderProps = WorkspaceHeaderProps | GreenRoomHeaderProps;

function ProfileAvatar({ label }: { label: string }) {
    return (
        <button
            aria-label={label}
            className='rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2'
            type='button'
        >
            <Avatar className='h-12 w-12 shadow-[0_14px_28px_-18px_rgba(26,115,232,0.85)]'>
                <AvatarFallback className='bg-[linear-gradient(135deg,_var(--avatar-gradient-navy-start)_0%,_var(--avatar-gradient-navy-end)_100%)] text-white'>
                    <User className='h-6 w-6' />
                </AvatarFallback>
            </Avatar>
        </button>
    );
}

function IconButton({
    label,
    size = 'md',
    children,
}: {
    label: string;
    size?: 'sm' | 'md';
    children: ReactNode;
}) {
    const sizeClass = size === 'sm' ? 'h-10 w-10' : 'h-11 w-11';
    return (
        <button
            aria-label={label}
            className={`inline-flex ${sizeClass} items-center justify-center rounded-full text-text-muted transition-colors hover:bg-primary-subtle hover:text-primary`}
            type='button'
        >
            {children}
        </button>
    );
}

function WorkspaceHeader(props: WorkspaceHeaderProps) {
    const {
        brand,
        brandHref,
        navItems,
        activeNavId,
        rightMode = 'compact',
    } = props;

    return (
        <header className='sticky top-0 z-40 border-b border-border bg-header-surface backdrop-blur'>
            <div className='mx-auto flex max-w-[1600px] items-center justify-between px-6 py-5 sm:px-8 lg:px-10'>
                <div className='flex items-center gap-8 lg:gap-14'>
                    <Link
                        className='inline-flex items-center gap-3 text-[2.05rem] font-semibold tracking-tight text-primary'
                        href={brandHref}
                    >
                        <Logo className='h-10 w-10 text-primary' decorative />
                        <span>{brand}</span>
                    </Link>

                    <nav className='hidden items-center gap-8 text-[1.08rem] sm:flex'>
                        {navItems.map((tab) => {
                            const isActive = tab.id === activeNavId;
                            return (
                                <Link
                                    aria-current={isActive ? 'page' : undefined}
                                    className={`border-b-[3px] pb-2 transition-colors ${
                                        isActive
                                            ? 'border-primary font-medium text-primary'
                                            : 'border-transparent text-text-secondary hover:text-primary'
                                    }`}
                                    href={tab.href}
                                    key={tab.id}
                                >
                                    {tab.label}
                                </Link>
                            );
                        })}
                    </nav>
                </div>

                <div className='flex items-center gap-3 sm:gap-4'>
                    {rightMode === 'search' ? (
                        <>
                            <div className='hidden items-center gap-3 rounded-full bg-surface-input px-5 py-3 text-text-subtle shadow-inner sm:flex sm:min-w-[290px]'>
                                <Search className='h-6 w-6' />
                                <span className='text-[1.05rem]'>
                                    {props.searchPlaceholder}
                                </span>
                            </div>
                            <IconButton label={props.notificationsLabel}>
                                <Bell className='h-6 w-6' />
                            </IconButton>
                        </>
                    ) : (
                        <IconButton label={props.helpLabel}>
                            <HelpCircle className='h-6 w-6' />
                        </IconButton>
                    )}

                    <LocaleToggle />
                    <ThemeToggle />
                    <ProfileMenu label={props.profileLabel} />
                </div>
            </div>
        </header>
    );
}

function GreenRoomHeader(props: GreenRoomHeaderProps) {
    const { brand, brandHref, helpLabel, settingsLabel, profileLabel } = props;

    return (
        <header className='sticky top-0 z-40 border-b border-border bg-header-surface-strong backdrop-blur'>
            <div className='mx-auto flex max-w-[1600px] items-center justify-between px-6 py-4 sm:px-8 lg:px-10'>
                <Link
                    className='inline-flex items-center gap-3 text-[1.85rem] font-semibold tracking-tight text-primary'
                    href={brandHref}
                >
                    <Logo className='h-9 w-9 text-primary' decorative />
                    <span>{brand}</span>
                </Link>

                <div className='flex items-center gap-4 sm:gap-5'>
                    <IconButton label={helpLabel}>
                        <HelpCircle className='h-6 w-6' />
                    </IconButton>
                    <IconButton label={settingsLabel}>
                        <Settings className='h-6 w-6' />
                    </IconButton>
                    <ProfileAvatar label={profileLabel} />
                </div>
            </div>
        </header>
    );
}

export function AppHeader(props: AppHeaderProps) {
    if (props.variant === 'workspace') {
        return <WorkspaceHeader {...props} />;
    }
    return <GreenRoomHeader {...props} />;
}
