'use client';

import { Check, Copy } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { cn } from '@/lib/utils.ts';

type UpcomingMeetingCardProps = {
    meeting: MeetingManagementMeetingResponse;
    copiedShortCode: string | null;
    onCardClick: (meeting: MeetingManagementMeetingResponse) => void;
    onCopyLink: (shortCode: string) => Promise<void>;
    onOpenSettings: (meetingId: string) => void;
    onCancel: (meeting: MeetingManagementMeetingResponse) => void;
    onEnd: (meeting: MeetingManagementMeetingResponse) => void;
};

function formatDateTimeRange(
    startTime: string | undefined,
    endTime: string | undefined,
): string {
    if (!startTime) return '';
    const start = new Date(startTime);
    const dateStr = start.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
    });
    const startTimeStr = start.toLocaleTimeString(undefined, {
        hour: 'numeric',
        minute: '2-digit',
    });
    if (!endTime) return `${dateStr} · ${startTimeStr}`;
    const end = new Date(endTime);
    const endTimeStr = end.toLocaleTimeString(undefined, {
        hour: 'numeric',
        minute: '2-digit',
    });
    return `${dateStr} · ${startTimeStr} – ${endTimeStr}`;
}

function StatusDot({ status }: { status: string | undefined }) {
    return (
        <span
            aria-hidden='true'
            className={cn(
                'inline-block h-2 w-2 shrink-0 rounded-full',
                status === 'LIVE'
                    ? 'bg-success ring-2 ring-success-subtle'
                    : 'bg-text-subtle/40',
            )}
        />
    );
}

/**
 * Renders a single upcoming host meeting as a compact row. Clicking the row
 * opens the detail sheet; inline actions support start/join, copy code and
 * scheduled meeting cancellation.
 */
export function UpcomingMeetingCard({
    meeting,
    copiedShortCode,
    onCardClick,
    onCopyLink,
    onCancel,
    onEnd,
}: UpcomingMeetingCardProps) {
    const t = useTranslations('workspace.home');
    const locale = useLocale();
    const router = useRouter();

    const title = meeting.title || t('untitledMeeting');
    const dateTimeRange = formatDateTimeRange(
        meeting.startTime,
        meeting.endTime,
    );
    const primaryActionLabel =
        meeting.status === 'LIVE' ? t('joinMeeting') : t('startMeeting');
    const isCopied =
        copiedShortCode != null && copiedShortCode === meeting.shortCode;
    const statusLabel =
        meeting.status === 'LIVE' ? t('statusLive') : t('statusScheduled');

    function handleRowClick() {
        onCardClick(meeting);
    }

    function handleRowKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onCardClick(meeting);
        }
    }

    function handlePrimaryAction(event: React.MouseEvent) {
        event.stopPropagation();
        if (!meeting.shortCode) return;
        router.push(
            `/${locale}/workspace/green-room?code=${meeting.shortCode}`,
        );
    }

    function handleCopyLink() {
        if (meeting.shortCode) {
            void onCopyLink(meeting.shortCode);
        }
    }

    function handleCancel() {
        onCancel(meeting);
    }

    function handleEnd() {
        onEnd(meeting);
    }

    return (
        <div className='group relative flex w-full items-center gap-3 rounded-2xl bg-surface px-5 py-4 shadow-card transition-shadow hover:shadow-card-hover'>
            <button
                aria-label={`${title} — ${statusLabel}`}
                className='flex min-w-0 flex-1 cursor-pointer items-center gap-3 rounded-xl text-left outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2'
                onClick={handleRowClick}
                onKeyDown={handleRowKeyDown}
                type='button'
            >
                <StatusDot status={meeting.status} />
                <div className='min-w-0 flex-1'>
                    <h3 className='truncate text-base font-semibold tracking-tight text-text-dark'>
                        {title}
                    </h3>
                    <p className='truncate text-xs text-text-muted'>
                        {dateTimeRange}
                    </p>
                </div>
            </button>

            <TooltipProvider delayDuration={150}>
                <div className='relative z-10 flex shrink-0 items-center gap-2'>
                    {meeting.shortCode && (
                        <Button
                            onClick={handlePrimaryAction}
                            size='sm'
                            type='button'
                        >
                            {primaryActionLabel}
                        </Button>
                    )}

                    <Tooltip>
                        <TooltipTrigger asChild>
                            <Button
                                aria-label={
                                    isCopied ? t('codeCopied') : t('copyCode')
                                }
                                disabled={!meeting.shortCode}
                                onClick={(event) => {
                                    event.stopPropagation();
                                    handleCopyLink();
                                }}
                                size='icon'
                                type='button'
                                variant='ghost'
                            >
                                {isCopied ? (
                                    <Check className='text-success' />
                                ) : (
                                    <Copy />
                                )}
                            </Button>
                        </TooltipTrigger>
                        <TooltipContent>
                            {isCopied ? t('codeCopied') : t('copyCode')}
                        </TooltipContent>
                    </Tooltip>

                    {meeting.status === 'SCHEDULED' && (
                        <Button
                            className='text-error hover:bg-error-subtle hover:text-error'
                            onClick={(event) => {
                                event.stopPropagation();
                                handleCancel();
                            }}
                            type='button'
                            variant='ghost'
                        >
                            {t('cancelMeeting')}
                        </Button>
                    )}
                    {meeting.status === 'LIVE' && (
                        <Button
                            className='text-error hover:bg-error-subtle hover:text-error'
                            onClick={(event) => {
                                event.stopPropagation();
                                handleEnd();
                            }}
                            type='button'
                            variant='ghost'
                        >
                            {t('endMeeting')}
                        </Button>
                    )}
                </div>
            </TooltipProvider>
        </div>
    );
}
