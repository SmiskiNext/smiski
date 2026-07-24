import { useMemo, useState } from 'react';
import {
    EmptyState,
    ErrorState,
    LoadingState,
    MeetingActionMenu,
    MeetingStatusTag,
} from '../../../components/shared';
import { Button, Icon } from '../../../components/ui';
import {
    getAvailableMeetingActions,
    type Meeting,
    type MeetingAction,
    type MeetingPermissions,
} from '../../../domain';

export interface MeetingListTableProps {
    meetings: Meeting[];
    permissions: MeetingPermissions;
    isLoading: boolean;
    error: Error | null;
    onSelect: (meeting: Meeting) => void;
    onAction: (action: MeetingAction, meeting: Meeting) => void;
}

type SortKey = 'title' | 'issueKey' | 'status' | 'scheduledAt';

function ActionsCell({
    meeting,
    permissions,
    onAction,
}: {
    meeting: Meeting;
    permissions: MeetingPermissions;
    onAction: (action: MeetingAction, meeting: Meeting) => void;
}) {
    const actions = getAvailableMeetingActions(meeting, permissions);
    const primaryAction = actions.includes('JOIN')
        ? 'JOIN'
        : actions.includes('START')
          ? 'START'
          : null;

    return (
        <div
            className='flex items-center justify-end gap-1'
            role='toolbar'
            aria-label='Meeting actions'
            onClick={(event) => event.stopPropagation()}
            onKeyDown={(event) => event.stopPropagation()}
        >
            {primaryAction && (
                <Button
                    size='sm'
                    variant={primaryAction === 'JOIN' ? 'primary' : 'secondary'}
                    className='h-7 min-h-0 rounded-md px-2.5'
                    onClick={() => onAction(primaryAction, meeting)}
                >
                    {primaryAction === 'JOIN' ? 'Join' : 'Start'}
                </Button>
            )}
            <MeetingActionMenu
                meetingId={meeting.id}
                meeting={meeting}
                permissions={permissions}
                hiddenActions={primaryAction ? [primaryAction] : []}
                onAction={(action) => onAction(action, meeting)}
            />
        </div>
    );
}

const columns: Array<{
    key: SortKey | 'host' | 'participants' | 'actions';
    label: string;
    sortable?: boolean;
    className?: string;
}> = [
    { key: 'title', label: 'Meeting', sortable: true, className: 'w-[38%]' },
    {
        key: 'issueKey',
        label: 'Issue',
        sortable: true,
        className: 'hidden w-[18%] sm:table-cell',
    },
    { key: 'status', label: 'Status', sortable: true, className: 'w-28' },
    { key: 'host', label: 'Host', className: 'hidden w-32 xl:table-cell' },
    {
        key: 'scheduledAt',
        label: 'Schedule / Started',
        sortable: true,
        className: 'hidden w-40 lg:table-cell',
    },
    {
        key: 'participants',
        label: 'People',
        className: 'hidden w-16 2xl:table-cell',
    },
    { key: 'actions', label: '', className: 'w-24' },
];

function meetingTime(meeting: Meeting): string {
    const value =
        meeting.status === 'SCHEDULED'
            ? meeting.scheduledAt
            : (meeting.startedAt ?? meeting.scheduledAt);
    return value ? new Date(value).toLocaleString() : '—';
}

const PAGE_SIZE = 20;

export function MeetingListTable({
    meetings,
    permissions,
    isLoading,
    error,
    onSelect,
    onAction,
}: MeetingListTableProps) {
    const [sortKey, setSortKey] = useState<SortKey>('scheduledAt');
    const [sortOrder, setSortOrder] = useState<'ASC' | 'DESC'>('DESC');
    const [page, setPage] = useState(1);
    const sortedMeetings = useMemo(
        () =>
            [...meetings].sort((a, b) => {
                const result = String(a[sortKey] ?? '').localeCompare(
                    String(b[sortKey] ?? ''),
                );
                return sortOrder === 'ASC' ? result : -result;
            }),
        [meetings, sortKey, sortOrder],
    );
    const pageCount = Math.max(1, Math.ceil(sortedMeetings.length / PAGE_SIZE));
    const currentPage = Math.min(page, pageCount);
    const pagedMeetings = sortedMeetings.slice(
        (currentPage - 1) * PAGE_SIZE,
        currentPage * PAGE_SIZE,
    );

    const changeSort = (key: SortKey) => {
        setPage(1);
        if (sortKey === key)
            setSortOrder((order) => (order === 'ASC' ? 'DESC' : 'ASC'));
        else {
            setSortKey(key);
            setSortOrder('ASC');
        }
    };

    return (
        <section>
            <div className='flex h-10 items-center justify-between border-b px-4 sm:px-6'>
                <h2 className='text-xs font-semibold text-[var(--text)]'>
                    All meetings
                </h2>
                <span className='text-xs tabular-nums text-[var(--text-faint)]'>
                    {meetings.length} total
                </span>
            </div>
            {isLoading ? (
                <div className='px-5'>
                    <LoadingState label='Loading meetings…' />
                </div>
            ) : error ? (
                <div className='p-5'>
                    <ErrorState message={error.message} />
                </div>
            ) : meetings.length === 0 ? (
                <div className='p-6'>
                    <EmptyState
                        header='No meetings found'
                        description='Try adjusting the active filters.'
                    />
                </div>
            ) : (
                <div>
                    <table className='w-full table-fixed border-separate border-spacing-0 text-left text-xs'>
                        <thead>
                            <tr>
                                {columns.map((column) => (
                                    <th
                                        key={column.key}
                                        className={`h-9 border-b bg-[var(--surface-soft)] px-3 text-[10px] font-semibold tracking-wide text-[var(--text-faint)] uppercase first:pl-6 ${column.className ?? ''}`}
                                    >
                                        {column.sortable ? (
                                            <button
                                                type='button'
                                                className='inline-flex items-center gap-1 hover:text-[var(--text)]'
                                                onClick={() =>
                                                    changeSort(
                                                        column.key as SortKey,
                                                    )
                                                }
                                            >
                                                {column.label}
                                                {sortKey === column.key && (
                                                    <Icon
                                                        name={
                                                            sortOrder === 'ASC'
                                                                ? 'chevronUp'
                                                                : 'chevronDown'
                                                        }
                                                        size={12}
                                                    />
                                                )}
                                            </button>
                                        ) : (
                                            column.label
                                        )}
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {pagedMeetings.map((meeting) => (
                                <tr
                                    key={meeting.id}
                                    tabIndex={0}
                                    className='group cursor-pointer outline-none hover:bg-[var(--surface-soft)] focus-visible:bg-brand-50 dark:focus-visible:bg-brand-500/10'
                                    onClick={() => onSelect(meeting)}
                                    onKeyDown={(event) => {
                                        if (event.key === 'Enter')
                                            onSelect(meeting);
                                    }}
                                >
                                    <td className='border-b px-3 py-2.5 pl-6'>
                                        <p className='max-w-sm truncate font-semibold text-[var(--text)]'>
                                            {meeting.title}
                                        </p>
                                        <p className='mt-0.5 max-w-sm truncate text-[11px] text-[var(--text-faint)]'>
                                            {meeting.description
                                                ?? `Created by ${meeting.creatorName}`}
                                        </p>
                                    </td>
                                    <td className='hidden border-b px-3 py-2.5 sm:table-cell'>
                                        <span className='font-medium text-brand-700 dark:text-brand-300'>
                                            {meeting.issueKey ?? '—'}
                                        </span>
                                        {meeting.issueSummary && (
                                            <p className='mt-0.5 max-w-36 truncate text-[11px] text-[var(--text-faint)]'>
                                                {meeting.issueSummary}
                                            </p>
                                        )}
                                    </td>
                                    <td className='border-b px-3 py-2.5'>
                                        <MeetingStatusTag
                                            status={meeting.status}
                                        />
                                    </td>
                                    <td className='hidden border-b px-3 py-2.5 text-[var(--text-muted)] xl:table-cell'>
                                        {meeting.hostName}
                                    </td>
                                    <td className='hidden border-b px-3 py-2.5 whitespace-nowrap text-[var(--text-muted)] lg:table-cell'>
                                        {meetingTime(meeting)}
                                    </td>
                                    <td className='hidden border-b px-3 py-2.5 text-center tabular-nums text-[var(--text-muted)] 2xl:table-cell'>
                                        {meeting.participantCount}
                                    </td>
                                    <td className='border-b px-3 py-2'>
                                        <ActionsCell
                                            meeting={meeting}
                                            permissions={permissions}
                                            onAction={onAction}
                                        />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {pageCount > 1 && (
                        <div className='flex items-center justify-between border-t px-4 py-2.5 sm:px-6'>
                            <span className='text-xs text-[var(--text-faint)]'>
                                Page {currentPage} of {pageCount}
                            </span>
                            <div className='flex items-center gap-1.5'>
                                <Button
                                    size='sm'
                                    variant='secondary'
                                    disabled={currentPage === 1}
                                    onClick={() =>
                                        setPage((p) => Math.max(1, p - 1))
                                    }
                                    leadingIcon={
                                        <Icon
                                            name='chevronDown'
                                            size={14}
                                            className='rotate-90'
                                        />
                                    }
                                >
                                    Previous
                                </Button>
                                <Button
                                    size='sm'
                                    variant='secondary'
                                    disabled={currentPage === pageCount}
                                    onClick={() =>
                                        setPage((p) =>
                                            Math.min(pageCount, p + 1),
                                        )
                                    }
                                >
                                    Next
                                    <Icon
                                        name='chevronDown'
                                        size={14}
                                        className='-rotate-90'
                                    />
                                </Button>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </section>
    );
}
