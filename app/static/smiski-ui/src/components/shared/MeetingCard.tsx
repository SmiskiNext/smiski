import type { ReactNode } from 'react';
import type { Meeting } from '../../domain';
import { cn, Icon } from '../ui';
import { MeetingStatusTag } from './StatusTag';

export interface MeetingCardProps {
    meeting: Meeting;
    actions?: ReactNode;
    density?: 'compact' | 'comfortable';
}

function formatStartTime(meeting: Meeting): string | null {
    const timestamp = meeting.scheduledAt ?? meeting.startedAt;
    if (!timestamp) return null;
    return new Date(timestamp).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
    });
}

export function MeetingCard({
    meeting,
    actions,
    density = 'comfortable',
}: MeetingCardProps) {
    const startLabel = formatStartTime(meeting);
    const isCompact = density === 'compact';

    return (
        <article
            className={cn(
                'relative overflow-visible rounded-md border bg-[var(--surface)] shadow-sm',
                isCompact ? 'p-3.5' : 'p-4',
            )}
        >
            <div className='flex items-start gap-3'>
                <div
                    className={cn(
                        'flex shrink-0 items-center justify-center rounded-md',
                        meeting.status === 'RUNNING'
                            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300'
                            : 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300',
                        isCompact ? 'size-9' : 'size-10',
                    )}
                >
                    <Icon
                        name={
                            meeting.status === 'RUNNING' ? 'video' : 'calendar'
                        }
                        size={18}
                    />
                </div>
                <div className='min-w-0 flex-1'>
                    <div className='flex flex-wrap items-start gap-2'>
                        <h4
                            className='min-w-0 flex-1 truncate text-sm font-bold text-[var(--text)]'
                            title={meeting.title}
                        >
                            {meeting.title}
                        </h4>
                        <MeetingStatusTag status={meeting.status} />
                    </div>
                    <div className='mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--text-muted)]'>
                        {meeting.issueKey && (
                            <span className='font-semibold text-brand-700 dark:text-brand-300'>
                                {meeting.issueKey}
                            </span>
                        )}
                        {startLabel && (
                            <span className='inline-flex items-center gap-1'>
                                <Icon name='clock' size={13} />
                                {startLabel}
                            </span>
                        )}
                    </div>
                    <p className='mt-1 truncate text-xs text-[var(--text-faint)]'>
                        Hosted by {meeting.hostName}
                    </p>
                </div>
                {actions && (
                    <div className='relative z-10 shrink-0'>{actions}</div>
                )}
            </div>
        </article>
    );
}
