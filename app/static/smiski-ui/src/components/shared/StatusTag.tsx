/**
 * StatusTag — compact dot + label treatment optimized for dense data tables.
 */
import type { MeetingStatus, RecordingStatus } from '../../domain';
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

const RECORDING_STATUS_CONFIG: Record<
    RecordingStatus,
    { className: string; text: string }
> = {
    PENDING: {
        className:
            'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
        text: 'Processing',
    },
    RECORDING: {
        className:
            'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
        text: 'Recording',
    },
    COMPLETED: {
        className:
            'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
        text: 'Recorded',
    },
    FAILED: {
        className:
            'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300',
        text: 'Failed',
    },
};

function LiveDot() {
    return (
        <span
            aria-hidden='true'
            className='mr-1.5 size-1.5 animate-pulse rounded-full bg-current'
        />
    );
}

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

export interface RecordingStatusTagProps {
    status: RecordingStatus;
}

export function RecordingStatusTag({ status }: RecordingStatusTagProps) {
    const config = RECORDING_STATUS_CONFIG[status];
    return (
        <span
            className={cn(
                'inline-flex items-center rounded-full px-2 py-1 text-[10px] font-bold tracking-wide uppercase',
                config.className,
            )}
        >
            {status === 'RECORDING' && <LiveDot />}
            {config.text}
        </span>
    );
}
