'use client';

import { History, Loader2, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import { Card } from '@/components/ui/card.tsx';
import { cn } from '@/lib/utils.ts';
import { MeetingHistoryCard } from './meeting-history-card.tsx';
import type { MeetingHistoryState } from './use-meeting-history.ts';

type MeetingHistoryListProps = {
    state: MeetingHistoryState;
    retry: () => void;
    refresh: () => void;
    loadMore: () => void;
};

const SKELETON_ITEMS = ['first', 'second', 'third', 'fourth', 'fifth'];

function MeetingHistorySkeleton() {
    return (
        <div className='space-y-4'>
            {SKELETON_ITEMS.map((item) => (
                <Card className='border-border bg-surface p-5' key={item}>
                    <div className='space-y-3 motion-safe:animate-pulse'>
                        <div className='h-5 w-1/3 rounded bg-surface-input' />
                        <div className='h-4 w-1/2 rounded bg-surface-input' />
                        <div className='h-4 w-1/4 rounded bg-surface-input' />
                    </div>
                </Card>
            ))}
        </div>
    );
}

export function MeetingHistoryList({
    state,
    retry,
    refresh,
    loadMore,
}: MeetingHistoryListProps) {
    const t = useTranslations('workspace.history');
    const locale = useLocale();
    const router = useRouter();

    function handleMeetingClick(meetingId: string | undefined) {
        if (!meetingId) return;
        router.push(`/${locale}/workspace/history/${meetingId}`);
    }

    return (
        <section className='mx-auto max-w-4xl space-y-6'>
            <div className='flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between'>
                <div>
                    <h1 className='text-3xl font-semibold tracking-tight text-text-darkest'>
                        {t('headline')}
                    </h1>
                    <p className='mt-2 text-sm text-text-muted'>
                        {t('description')}
                    </p>
                </div>
                {state.phase !== 'LOADING' && (
                    <Button
                        aria-label={t('refreshAriaLabel')}
                        disabled={
                            state.phase === 'SUCCESS' && state.isRefreshing
                        }
                        onClick={refresh}
                        type='button'
                        variant='outline'
                    >
                        <RefreshCw
                            className={cn(
                                state.phase === 'SUCCESS'
                                    && state.isRefreshing
                                    && 'animate-spin',
                            )}
                        />
                        {t('refresh')}
                    </Button>
                )}
            </div>

            <div className='rounded-3xl border border-border bg-surface p-6 shadow-card'>
                <div className='mb-6 flex items-center justify-between gap-4'>
                    <h2 className='text-xl font-semibold text-text-darkest'>
                        {t('sectionTitle')}
                    </h2>
                </div>

                {state.phase === 'LOADING' && <MeetingHistorySkeleton />}

                {state.phase === 'EMPTY' && (
                    <div className='flex flex-col items-center justify-center rounded-2xl border border-dashed border-border px-6 py-14 text-center'>
                        <span className='inline-flex h-14 w-14 items-center justify-center rounded-full bg-primary-subtle text-primary'>
                            <History className='h-7 w-7' />
                        </span>
                        <h3 className='mt-5 text-xl font-semibold text-text-darkest'>
                            {t('emptyTitle')}
                        </h3>
                        <p className='mt-2 max-w-md text-sm text-text-muted'>
                            {t('emptyDescription')}
                        </p>
                        <Button asChild className='mt-6' type='button'>
                            <Link href={`/${locale}/workspace`}>
                                {t('emptyCta')}
                            </Link>
                        </Button>
                    </div>
                )}

                {state.phase === 'ERROR' && (
                    <div className='flex flex-col items-center justify-center rounded-2xl border border-error/30 bg-error-subtle/40 px-6 py-14 text-center'>
                        <h3 className='text-xl font-semibold text-error-dark'>
                            {t('errorTitle')}
                        </h3>
                        <p className='mt-2 max-w-md text-sm text-text-muted'>
                            {state.message}
                        </p>
                        <Button
                            className='mt-6'
                            onClick={retry}
                            type='button'
                            variant='outline'
                        >
                            {t('errorRetry')}
                        </Button>
                    </div>
                )}

                {state.phase === 'SUCCESS' && (
                    <div
                        className={cn(
                            'space-y-4',
                            state.isRefreshing && 'opacity-70',
                        )}
                    >
                        {state.meetings.map((meeting) => (
                            <MeetingHistoryCard
                                key={meeting.id}
                                meeting={meeting}
                                onClick={(selectedMeeting) =>
                                    handleMeetingClick(selectedMeeting.id)
                                }
                            />
                        ))}

                        {state.nextPageToken && (
                            <div className='flex justify-center pt-4'>
                                <Button
                                    disabled={state.isLoadingMore}
                                    onClick={loadMore}
                                    type='button'
                                    variant='outline'
                                >
                                    {state.isLoadingMore && (
                                        <Loader2 className='animate-spin' />
                                    )}
                                    {state.isLoadingMore
                                        ? t('loadMoreLoading')
                                        : t('loadMore')}
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </section>
    );
}
