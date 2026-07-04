'use client';

import {
    ArrowLeft,
    CalendarDays,
    Check,
    Clock,
    Copy,
    Loader2,
    Play,
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { Badge } from '@/components/ui/badge.tsx';
import { Button } from '@/components/ui/button.tsx';
import { Card } from '@/components/ui/card.tsx';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip.tsx';
import type {
    MeetingManagementMeetingDetailResponse,
    MeetingManagementRecordingResponse,
} from '@/generated/types.gen.ts';
import { cn } from '@/lib/utils.ts';
import { ParticipantList } from './participant-list.tsx';
import { RecordingPlayer } from './recording-player.tsx';
import { useMeetingDetail } from './use-meeting-detail.ts';

type MeetingDetailScreenProps = {
    meetingId: string;
};

function formatFullDate(value: string | undefined, locale: string): string {
    if (!value) return '';
    return new Date(value).toLocaleDateString(locale, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
    });
}

function formatTime(value: string | undefined, locale: string): string {
    if (!value) return '';
    return new Date(value).toLocaleTimeString(locale, {
        hour: 'numeric',
        minute: '2-digit',
    });
}

function formatDuration(totalSeconds: number | undefined): string | null {
    if (!totalSeconds || totalSeconds <= 0) return null;
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = Math.floor(totalSeconds % 60);
    const parts = hours > 0 ? [hours, minutes, seconds] : [minutes, seconds];
    return parts.map((part) => String(part).padStart(2, '0')).join(':');
}

function getDurationSeconds(
    detail: MeetingManagementMeetingDetailResponse,
): number | null {
    if (!detail.startTime || !detail.endTime) return null;
    const durationMs =
        new Date(detail.endTime).getTime()
        - new Date(detail.startTime).getTime();
    if (!Number.isFinite(durationMs) || durationMs <= 0) return null;
    return Math.round(durationMs / 1000);
}

function getTypeLabel(
    t: ReturnType<typeof useTranslations<'workspace.history'>>,
    type: MeetingManagementMeetingDetailResponse['type'],
): string | null {
    if (!type) return null;
    return type === 'INSTANT' ? t('typeInstant') : t('typeScheduled');
}

function getStatusLabel(
    t: ReturnType<typeof useTranslations<'workspace.history'>>,
    status: MeetingManagementMeetingDetailResponse['status'],
): string | null {
    if (!status) return null;
    const labels = {
        SCHEDULED: t('statusScheduled'),
        LIVE: t('statusLive'),
        ENDED: t('statusEnded'),
        CANCELLED: t('statusCancelled'),
    } as const;
    return labels[status];
}

function RecordingRow({
    recording,
    index,
    onSelect,
}: {
    recording: MeetingManagementRecordingResponse;
    index: number;
    onSelect: (recording: MeetingManagementRecordingResponse) => void;
}) {
    const t = useTranslations('workspace.history');
    const locale = useLocale();
    const label = t('recordingRowLabel', { index: index + 1 });
    const createdDate = recording.createdAt
        ? new Date(recording.createdAt).toLocaleDateString(locale, {
              month: 'short',
              day: 'numeric',
              year: 'numeric',
          })
        : '';
    const duration =
        formatDuration(recording.durationSeconds)
        ?? t('recordingDurationFallback');

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSelect(recording);
        }
    }

    return (
        <Card
            className='cursor-pointer border-border bg-background p-4 transition-shadow hover:shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2'
            onClick={() => onSelect(recording)}
            onKeyDown={handleKeyDown}
            role='button'
            tabIndex={0}
        >
            <div className='flex items-center justify-between gap-4'>
                <div className='flex min-w-0 items-center gap-3'>
                    <span className='inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-primary'>
                        <Play className='h-5 w-5' />
                    </span>
                    <div className='min-w-0'>
                        <h3 className='truncate text-sm font-semibold text-text-darkest'>
                            {label}
                        </h3>
                        {createdDate && (
                            <p className='mt-1 text-xs text-text-muted'>
                                {createdDate}
                            </p>
                        )}
                    </div>
                </div>
                <span className='shrink-0 text-sm text-text-muted'>
                    {duration}
                </span>
            </div>
        </Card>
    );
}

const COPY_FEEDBACK_DURATION_MS = 2000;

function MeetingDetailContent({
    detail,
}: {
    detail: MeetingManagementMeetingDetailResponse;
}) {
    const t = useTranslations('workspace.history');
    const locale = useLocale();
    const router = useRouter();
    const [selectedRecordingUrl, setSelectedRecordingUrl] = useState<
        string | null
    >(null);
    const [playerOpen, setPlayerOpen] = useState(false);
    const [isCopied, setIsCopied] = useState(false);
    const resetCopiedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(
        null,
    );
    const title = detail.title?.trim() || t('untitledMeeting');
    const typeLabel = getTypeLabel(t, detail.type);
    const statusLabel = getStatusLabel(t, detail.status);
    const fullDate = formatFullDate(detail.startTime, locale);
    const startTime = formatTime(detail.startTime, locale);
    const endTime = formatTime(detail.endTime, locale);
    const duration = formatDuration(getDurationSeconds(detail) ?? undefined);
    const timeRange =
        startTime && endTime && duration
            ? t('timeRangeWithDuration', { startTime, endTime, duration })
            : startTime
              ? t('timeStartOnly', { startTime })
              : '';
    const description = detail.description?.trim();
    const recordings = detail.recordings ?? [];

    useEffect(() => {
        return () => {
            if (resetCopiedTimerRef.current) {
                clearTimeout(resetCopiedTimerRef.current);
            }
        };
    }, []);

    function openRecording(recording: MeetingManagementRecordingResponse) {
        setSelectedRecordingUrl(recording.fileUrl?.trim() || null);
        setPlayerOpen(true);
    }

    function closeRecording() {
        setPlayerOpen(false);
        setSelectedRecordingUrl(null);
    }

    async function handleCopyCode() {
        if (!detail.shortCode) return;

        try {
            await navigator.clipboard.writeText(detail.shortCode);
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

    return (
        <div className='mx-auto max-w-4xl space-y-6'>
            <Button
                onClick={() => router.push(`/${locale}/workspace/history`)}
                type='button'
                variant='ghost'
            >
                <ArrowLeft />
                {t('detailBack')}
            </Button>

            <Card className='border-border bg-surface p-6 shadow-card'>
                <div className='space-y-5'>
                    <div className='space-y-3'>
                        <div className='flex flex-wrap items-start justify-between gap-3'>
                            <h1 className='text-3xl font-semibold tracking-tight text-text-darkest'>
                                {title}
                            </h1>
                            {detail.shortCode && (
                                <TooltipProvider delayDuration={150}>
                                    <Tooltip>
                                        <TooltipTrigger asChild>
                                            <Button
                                                aria-label={
                                                    isCopied
                                                        ? t('codeCopied')
                                                        : t('copyCode')
                                                }
                                                onClick={() =>
                                                    void handleCopyCode()
                                                }
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
                                            {isCopied
                                                ? t('codeCopied')
                                                : t('copyCode')}
                                        </TooltipContent>
                                    </Tooltip>
                                </TooltipProvider>
                            )}
                        </div>
                        <div className='flex flex-wrap gap-2'>
                            {typeLabel && (
                                <Badge variant='secondary'>
                                    {t('detailTypeBadge', { type: typeLabel })}
                                </Badge>
                            )}
                            {statusLabel && (
                                <Badge
                                    className={cn(
                                        detail.status === 'CANCELLED'
                                            && 'border-error/20 bg-error-subtle text-error-dark hover:bg-error-subtle',
                                    )}
                                    variant={
                                        detail.status === 'CANCELLED'
                                            ? 'outline'
                                            : 'secondary'
                                    }
                                >
                                    {t('detailStatusBadge', {
                                        status: statusLabel,
                                    })}
                                </Badge>
                            )}
                        </div>
                    </div>

                    <div className='space-y-2 text-sm text-text-muted'>
                        {fullDate && (
                            <p className='flex items-center gap-2'>
                                <CalendarDays className='h-4 w-4' />
                                <span>{fullDate}</span>
                            </p>
                        )}
                        {timeRange && (
                            <p className='flex items-center gap-2'>
                                <Clock className='h-4 w-4' />
                                <span>{timeRange}</span>
                            </p>
                        )}
                    </div>
                </div>
            </Card>

            {description && (
                <section className='rounded-3xl border border-border bg-surface p-6 shadow-card'>
                    <h2 className='text-xl font-semibold text-text-darkest'>
                        {t('descriptionSectionTitle')}
                    </h2>
                    <p className='mt-4 whitespace-pre-wrap text-sm leading-6 text-text-muted'>
                        {description}
                    </p>
                </section>
            )}

            <ParticipantList participants={detail.participants} />

            {recordings.length > 0 && (
                <section className='rounded-3xl border border-border bg-surface p-6 shadow-card'>
                    <h2 className='text-xl font-semibold text-text-darkest'>
                        {t('recordingsSectionTitle', {
                            count: recordings.length,
                        })}
                    </h2>
                    <div className='mt-5 space-y-3'>
                        {recordings.map((recording, index) => (
                            <RecordingRow
                                index={index}
                                key={
                                    recording.id
                                    ?? `${recording.fileUrl ?? 'recording'}-${index}`
                                }
                                onSelect={openRecording}
                                recording={recording}
                            />
                        ))}
                    </div>
                </section>
            )}

            <RecordingPlayer
                onClose={closeRecording}
                open={playerOpen}
                url={selectedRecordingUrl}
            />
        </div>
    );
}

export function MeetingDetailScreen({ meetingId }: MeetingDetailScreenProps) {
    const t = useTranslations('workspace.history');
    const { state, retry } = useMeetingDetail(meetingId);

    if (state.phase === 'LOADING') {
        return (
            <div className='flex min-h-[50vh] flex-col items-center justify-center gap-3 text-text-muted'>
                <Loader2 className='h-8 w-8 animate-spin' />
                <p>{t('detailLoading')}</p>
            </div>
        );
    }

    if (state.phase === 'ERROR') {
        return (
            <div className='mx-auto flex max-w-2xl flex-col items-center justify-center rounded-3xl border border-error/30 bg-error-subtle/40 px-6 py-14 text-center'>
                <h1 className='text-2xl font-semibold text-error-dark'>
                    {t('detailErrorTitle')}
                </h1>
                <p className='mt-2 text-sm text-text-muted'>{state.message}</p>
                <Button
                    className='mt-6'
                    onClick={retry}
                    type='button'
                    variant='outline'
                >
                    {t('detailErrorRetry')}
                </Button>
            </div>
        );
    }

    return <MeetingDetailContent detail={state.detail} />;
}
