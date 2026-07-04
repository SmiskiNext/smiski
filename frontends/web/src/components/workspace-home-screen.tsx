'use client';

import { Plus } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { NewMeetingDropdown } from '@/components/create-meeting/new-meeting-dropdown.tsx';
import { Button } from '@/components/ui/button.tsx';
import { Input } from '@/components/ui/input.tsx';
import { UpcomingMeetingList } from '@/components/upcoming-meetings/index.ts';
import { WorkspaceShell } from '@/components/workspace-shell.tsx';

function JoinIcon() {
    return (
        <svg
            aria-hidden='true'
            className='h-7 w-7'
            fill='currentColor'
            viewBox='0 0 24 24'
        >
            <path d='M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm5 4h2v10h-2V7Zm-4 4h10v2H7v-2Z' />
        </svg>
    );
}

function CalendarIcon() {
    return (
        <svg
            aria-hidden='true'
            className='h-7 w-7'
            fill='none'
            stroke='currentColor'
            strokeWidth='2'
            viewBox='0 0 24 24'
        >
            <rect x='4' y='5' width='16' height='15' rx='2' />
            <path d='M8 3v4M16 3v4M4 10h16' />
        </svg>
    );
}

function ArrowIcon() {
    return (
        <svg
            aria-hidden='true'
            className='h-8 w-8'
            fill='none'
            stroke='currentColor'
            strokeLinecap='round'
            strokeWidth='2.2'
            viewBox='0 0 24 24'
        >
            <path d='M5 12h14M13 5l7 7-7 7' />
        </svg>
    );
}

export function WorkspaceHomeScreen() {
    const t = useTranslations('workspace.home');
    const common = useTranslations('workspace.common');
    const locale = useLocale();
    const [meetingCode, setMeetingCode] = useState('');
    const trimmedCode = meetingCode.trim();

    return (
        <WorkspaceShell activeTab='home'>
            <section>
                <div className='flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between'>
                    <div className='max-w-[980px]'>
                        <h1 className='text-6xl font-semibold leading-[0.98] tracking-tight text-text-dark sm:text-7xl lg:text-[5.4rem]'>
                            {t('headline')}
                        </h1>
                        <p className='mt-6 max-w-[900px] text-2xl leading-[1.45] text-text-secondary sm:text-[1.18rem] sm:leading-10'>
                            {t('description')}
                        </p>
                    </div>

                    <NewMeetingDropdown>
                        <Button
                            aria-label={t('createMeeting')}
                            className='h-14 gap-3 self-start rounded-2xl px-7 text-lg shadow-[0_22px_46px_-24px_rgba(26,115,232,0.95)] hover:-translate-y-0.5 hover:shadow-[0_28px_52px_-24px_rgba(26,115,232,0.95)] lg:self-end'
                            size='lg'
                            type='button'
                        >
                            <Plus className='h-5 w-5' />
                            {t('createMeeting')}
                        </Button>
                    </NewMeetingDropdown>
                </div>

                <div className='mt-14 grid gap-6 md:grid-cols-2'>
                    <article className='flex min-h-[370px] flex-col rounded-[2rem] bg-surface-input p-10 shadow-[0_26px_70px_-38px_rgba(15,23,42,0.18)]'>
                        <span className='flex h-18 w-18 items-center justify-center rounded-full bg-surface-input-alt text-brand-blue'>
                            <JoinIcon />
                        </span>
                        <h2 className='mt-10 text-[2.15rem] font-semibold tracking-tight text-text-dark'>
                            {t('cards.joinMeeting.title')}
                        </h2>
                        <p className='mt-4 text-xl leading-8 text-text-muted sm:text-[1.1rem]'>
                            {t('cards.joinMeeting.description')}
                        </p>
                        <div className='mt-auto flex flex-col gap-4 sm:flex-row'>
                            <Input
                                className='h-14 flex-1 rounded-full border-0 bg-surface px-5 text-lg shadow-none focus-visible:ring-2 focus-visible:ring-primary'
                                onChange={(event) =>
                                    setMeetingCode(event.target.value)
                                }
                                placeholder={t('cards.joinMeeting.placeholder')}
                                type='text'
                                value={meetingCode}
                            />
                            <Button
                                asChild
                                className={`h-14 rounded-full px-8 text-xl font-semibold ${
                                    trimmedCode
                                        ? 'shadow-[0_18px_34px_-22px_rgba(26,115,232,0.95)] hover:bg-primary-hover'
                                        : 'pointer-events-none bg-surface-input-alt text-text-disabled shadow-none hover:bg-surface-input-alt'
                                }`}
                                size='lg'
                            >
                                <Link
                                    aria-disabled={!trimmedCode}
                                    href={`/${locale}/workspace/green-room?code=${trimmedCode}`}
                                    tabIndex={trimmedCode ? 0 : -1}
                                >
                                    {common('join')}
                                </Link>
                            </Button>
                        </div>
                    </article>

                    <Link
                        className='flex min-h-[370px] flex-col rounded-[2rem] bg-surface p-10 shadow-[0_26px_70px_-38px_rgba(15,23,42,0.22)] transition-shadow hover:shadow-[0_30px_80px_-38px_rgba(15,23,42,0.3)]'
                        href={`/${locale}/workspace/schedule`}
                    >
                        <span className='flex h-18 w-18 items-center justify-center rounded-full bg-primary-subtle text-brand-blue'>
                            <CalendarIcon />
                        </span>
                        <h2 className='mt-10 text-[2.15rem] font-semibold tracking-tight text-text-dark'>
                            {t('cards.schedule.title')}
                        </h2>
                        <p className='mt-4 text-xl leading-8 text-text-muted sm:text-[1.1rem]'>
                            {t('cards.schedule.description')}
                        </p>
                        <span className='mt-auto self-end text-brand-blue transition-transform hover:translate-x-1'>
                            <ArrowIcon />
                        </span>
                    </Link>
                </div>
            </section>

            <section className='mt-16'>
                <div className='flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between'>
                    <h2 className='text-4xl font-semibold tracking-tight text-text-dark'>
                        {t('upcomingTitle')}
                    </h2>
                    <Link
                        className='text-xl font-medium text-primary transition-colors hover:text-primary-hover'
                        href={`/${locale}/workspace/schedule`}
                    >
                        {t('viewCalendar')}
                    </Link>
                </div>

                <UpcomingMeetingList />
            </section>
        </WorkspaceShell>
    );
}
