/**
 * StatusTag — compact dot + label treatment optimized for dense data tables.
 */
import type { MeetingStatus } from '../../domain';
import { cn } from '../ui';

const MEETING_STATUS_CONFIG: Record<
    MeetingStatus,
    { dotClassName: string; textClassName: string; text: string }
> = {
    SCHEDULED: {
        dotClassName: 'bg-blue-500',
        textClassName: 'text-blue-700 dark:text-blue-300',
        text: 'Scheduled',
    },
    RUNNING: {
        dotClassName: 'bg-emerald-500',
        textClassName: 'text-emerald-700 dark:text-emerald-300',
        text: 'Running',
    },
    COMPLETED: {
        dotClassName: 'bg-slate-400',
        textClassName: 'text-slate-600 dark:text-slate-300',
        text: 'Completed',
    },
    CANCELED: {
        dotClassName: 'bg-rose-500',
        textClassName: 'text-rose-700 dark:text-rose-300',
        text: 'Canceled',
    },
};

export interface MeetingStatusTagProps {
    status: MeetingStatus;
}

export function MeetingStatusTag({ status }: MeetingStatusTagProps) {
    const config = MEETING_STATUS_CONFIG[status];
    return (
        <span
            className={cn(
                'inline-flex items-center gap-2 text-xs font-medium whitespace-nowrap',
                config.textClassName,
            )}
        >
            <span
                className={cn(
                    'size-2 rounded-full',
                    config.dotClassName,
                    status === 'RUNNING' && 'ring-2 ring-emerald-500/20',
                )}
                aria-hidden='true'
            />
            <span>{config.text}</span>
        </span>
    );
}
