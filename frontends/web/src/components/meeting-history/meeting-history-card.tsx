'use client';

import { CalendarDays, Check, Clock, Copy } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import {
    type KeyboardEvent,
    type MouseEvent,
    useEffect,
    useRef,
    useState,
} from 'react';
import { Badge } from '@/components/ui/badge.tsx';
import { Button } from '@/components/ui/button.tsx';
import { Card } from '@/components/ui/card.tsx';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { cn } from '@/lib/utils.ts';

type MeetingHistoryCardProps = {
    meeting: MeetingManagementMeetingResponse;
    onClick: (meeting: MeetingManagementMeetingResponse) => void;
};

function formatDateTime(startTime: string | undefined, locale: string): string {
    if (!startTime) return '';
    const start = new Date(startTime);
    const date = start.toLocaleDateString(locale, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
    });
    const time = start.toLocaleTimeString(locale, {
        hour: 'numeric',
        minute: '2-digit',
    });
    return `${date} · ${time}`;
}

function getDurationMinutes(
    meeting: MeetingManagementMeetingResponse,
): number | null {
    if (!meeting.startTime || !meeting.endTime) return null;
    const durationMs =
        new Date(meeting.endTime).getTime()
        - new Date(meeting.startTime).getTime();
    if (!Number.isFinite(durationMs) || durationMs <= 0) return null;
    return Math.max(1, Math.round(durationMs / 60000));
}

function StatusDot({
    status,
}: {
    status: MeetingManagementMeetingResponse['status'];
}) {
    return (
        <span
            aria-hidden='true'
            className={cn(
                'mt-1 inline-block h-2.5 w-2.5 shrink-0 rounded-full',
                status === 'CANCELLED'
                    ? 'bg-error'
                    : 'bg-success ring-2 ring-success-subtle',
            )}
        />
    );
}

const COPY_FEEDBACK_DURATION_MS = 2000;

export function MeetingHistoryCard({
    meeting,
    onClick,
}: MeetingHistoryCardProps) {
    const t = useTranslations('workspace.history');
    const locale = useLocale();
    const [isCopied, setIsCopied] = useState(false);
    const resetCopiedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
        null,
    );
    const title = meeting.title?.trim() || t('untitledMeeting');
    const isCancelled = meeting.status === 'CANCELLED';
    const dateTime = formatDateTime(meeting.startTime, locale);
    const durationMinutes = getDurationMinutes(meeting);
    const typeLabel =
        meeting.type === 'INSTANT' ? t('typeInstant') : t('typeScheduled');
    const cancelledSuffix = t('cancelledAriaSuffix');
    const ariaLabel = isCancelled ? `${title} — ${cancelledSuffix}` : title;

    useEffect(() => {
        return () => {
            if (resetCopiedTimerRef.current) {
                clearTimeout(resetCopiedTimerRef.current);
            }
        };
    }, []);

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onClick(meeting);
        }
    }

    async function handleCopyClick(event: MouseEvent<HTMLButtonElement>) {
        event.stopPropagation();
        if (!meeting.shortCode) return;

        try {
            await navigator.clipboard.writeText(meeting.shortCode);
            setIsCopied(true);
            if (resetCopiedTimerRef.current) {
                clearTimeout(resetCopiedTimerRef.current);
            }
            resetCopiedTimerRef.current = setTimeout(() => {
                setIsCopied(false);
                resetCopiedTimerRef.current = null;
            }, COPY_FEEDBACK_DURATION_MS);
        } catch {
            return;
        }
    }

    function handleCopyKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
        event.stopPropagation();
    }

    return (
        <Card
            aria-label={ariaLabel}
            className={cn(
                'cursor-pointer border-border bg-surface p-5 shadow-card transition-shadow hover:shadow-card-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
                isCancelled && 'opacity-70',
            )}
            onClick={() => onClick(meeting)}
            onKeyDown={handleKeyDown}
            role='button'
            tabIndex={0}
        >
            <div className='flex items-start gap-3'>
                <StatusDot status={meeting.status} />
                <div className='min-w-0 flex-1 space-y-3'>
                    <div className='flex flex-wrap items-center gap-2'>
                        <h3
                            className={cn(
                                'truncate text-base font-semibold tracking-tight text-text-darkest',
                                isCancelled && 'line-through',
                            )}
                        >
                            {title}
                        </h3>
                        {isCancelled && (
                            <Badge className='border-error/20 bg-error-subtle text-error-dark hover:bg-error-subtle'>
                                {t('statusCancelled')}
                            </Badge>
                        )}
                    </div>
                    <div className='space-y-1.5 text-sm text-text-muted'>
                        {dateTime && (
                            <p className='flex items-center gap-2'>
                                <CalendarDays className='h-4 w-4' />
                                <span>{dateTime}</span>
                            </p>
                        )}
                        <p className='flex items-center gap-2'>
                            <Clock className='h-4 w-4' />
                            <span>
                                {durationMinutes === null
                                    ? t('durationMinutes', { count: 0 })
                                    : t('durationMinutes', {
                                          count: durationMinutes,
                                      })}
                            </span>
                        </p>
                    </div>
                </div>
                <div className='flex shrink-0 items-center gap-2'>
                    <Badge variant='secondary'>{typeLabel}</Badge>
                    {meeting.shortCode && (
                        <TooltipProvider delayDuration={150}>
                            <Tooltip>
                                <TooltipTrigger asChild>
                                    <Button
                                        aria-label={
                                            isCopied
                                                ? t('codeCopied')
                                                : t('copyCode')
                                        }
                                        onClick={handleCopyClick}
                                        onKeyDown={handleCopyKeyDown}
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
                        </TooltipProvider>
                    )}
                </div>
            </div>
        </Card>
    );
}
