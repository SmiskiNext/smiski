import { useMemo, useState } from 'react';
import {
    EmptyState,
    ErrorState,
    LoadingState,
    MeetingActionMenu,
    MeetingStatusTag,
} from '../../../components/shared';
import { Button, Icon, Modal } from '../../../components/ui';
import { useCurrentUser } from '../../../context/CurrentUserContext';
import {
    getAvailableMeetingActions,
    type Meeting,
    type MeetingAction,
    type MeetingPermissions,
} from '../../../domain';
import { useBatchDeleteMeetings } from '../../../hooks';

export interface MeetingListTableProps {
    meetings: Meeting[];
    permissions: MeetingPermissions;
    isLoading: boolean;
    error: Error | null;
    onSelect: (meeting: Meeting) => void;
    onAction: (action: MeetingAction, meeting: Meeting) => void;
    onBatchDeleteSuccess?: () => void;
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
    const currentUser = useCurrentUser();
    const actions = getAvailableMeetingActions(
        meeting,
        permissions,
        currentUser.accountId,
    );
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
    key: SortKey | 'select' | 'host' | 'participants' | 'actions';
    label: string;
    sortable?: boolean;
    className?: string;
}> = [
    { key: 'select', label: '', className: 'w-10 pl-4 sm:pl-6' },
    { key: 'title', label: 'Meeting', sortable: true, className: 'w-[36%]' },
    {
        key: 'issueKey',
        label: 'Issue',
        sortable: true,
        className: 'hidden w-[16%] sm:table-cell',
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
    onBatchDeleteSuccess,
}: MeetingListTableProps) {
    const [sortKey, setSortKey] = useState<SortKey>('scheduledAt');
    const [sortOrder, setSortOrder] = useState<'ASC' | 'DESC'>('DESC');
    const [page, setPage] = useState(1);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [batchDeleteError, setBatchDeleteError] = useState<string | null>(
        null,
    );

    const batchDeleteMutation = useBatchDeleteMeetings();

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

    const isAllPageSelected =
        pagedMeetings.length > 0
        && pagedMeetings.every((m) => selectedIds.has(m.id));

    const toggleSelectAllPage = () => {
        const next = new Set(selectedIds);
        if (isAllPageSelected) {
            for (const m of pagedMeetings) {
                next.delete(m.id);
            }
        } else {
            for (const m of pagedMeetings) {
                next.add(m.id);
            }
        }
        setSelectedIds(next);
    };

    const toggleSelectRow = (
        id: string,
        event: React.MouseEvent | React.ChangeEvent,
    ) => {
        event.stopPropagation();
        const next = new Set(selectedIds);
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        setSelectedIds(next);
    };

    const clearSelection = () => {
        setSelectedIds(new Set());
    };

    const handleConfirmBatchDelete = async () => {
        const ids = Array.from(selectedIds);
        if (ids.length === 0) return;
        setBatchDeleteError(null);
        try {
            await batchDeleteMutation.mutateAsync(ids);
            clearSelection();
            setShowDeleteConfirm(false);
            onBatchDeleteSuccess?.();
        } catch (err) {
            setBatchDeleteError(
                err instanceof Error
                    ? err.message
                    : 'Failed to delete selected meetings.',
            );
        }
    };

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
                {selectedIds.size > 0 ? (
                    <div className='flex w-full items-center justify-between'>
                        <span className='text-xs font-semibold text-brand-700 dark:text-brand-300'>
                            {selectedIds.size} meeting
                            {selectedIds.size > 1 ? 's' : ''} selected
                        </span>
                        <div className='flex items-center gap-2'>
                            <Button
                                size='sm'
                                variant='ghost'
                                className='h-7 min-h-0 text-xs'
                                onClick={clearSelection}
                            >
                                Deselect all
                            </Button>
                            <Button
                                size='sm'
                                variant='danger'
                                className='h-7 min-h-0 text-xs'
                                disabled={!permissions.hasEditMeeting}
                                onClick={() => setShowDeleteConfirm(true)}
                                leadingIcon={<Icon name='trash' size={13} />}
                            >
                                Delete selected
                            </Button>
                        </div>
                    </div>
                ) : (
                    <>
                        <h2 className='text-xs font-semibold text-[var(--text)]'>
                            All meetings
                        </h2>
                        <span className='text-xs tabular-nums text-[var(--text-faint)]'>
                            {meetings.length} total
                        </span>
                    </>
                )}
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
                                {columns.map((column) => {
                                    if (column.key === 'select') {
                                        return (
                                            <th
                                                key='select'
                                                className='h-9 w-10 border-b bg-[var(--surface-soft)] pl-4 sm:pl-6'
                                            >
                                                <input
                                                    type='checkbox'
                                                    aria-label='Select all meetings on current page'
                                                    className='h-3.5 w-3.5 cursor-pointer rounded border-slate-300 text-brand-600 focus:ring-brand-500'
                                                    checked={isAllPageSelected}
                                                    onChange={
                                                        toggleSelectAllPage
                                                    }
                                                />
                                            </th>
                                        );
                                    }
                                    return (
                                        <th
                                            key={column.key}
                                            className={`h-9 border-b bg-[var(--surface-soft)] px-3 text-[10px] font-semibold tracking-wide text-[var(--text-faint)] uppercase ${column.className ?? ''}`}
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
                                                                sortOrder
                                                                === 'ASC'
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
                                    );
                                })}
                            </tr>
                        </thead>
                        <tbody>
                            {pagedMeetings.map((meeting) => {
                                const isSelected = selectedIds.has(meeting.id);
                                return (
                                    <tr
                                        key={meeting.id}
                                        tabIndex={0}
                                        className={`group cursor-pointer outline-none hover:bg-[var(--surface-soft)] focus-visible:bg-brand-50 dark:focus-visible:bg-brand-500/10 ${
                                            isSelected
                                                ? 'bg-brand-50/50 dark:bg-brand-500/10'
                                                : ''
                                        }`}
                                        onClick={() => onSelect(meeting)}
                                        onKeyDown={(event) => {
                                            if (event.key === 'Enter')
                                                onSelect(meeting);
                                        }}
                                    >
                                        <td
                                            className='border-b px-3 py-2.5 pl-4 sm:pl-6'
                                            onClick={(e) => e.stopPropagation()}
                                        >
                                            <input
                                                type='checkbox'
                                                aria-label={`Select ${meeting.title}`}
                                                className='h-3.5 w-3.5 cursor-pointer rounded border-slate-300 text-brand-600 focus:ring-brand-500'
                                                checked={isSelected}
                                                onChange={(e) =>
                                                    toggleSelectRow(
                                                        meeting.id,
                                                        e,
                                                    )
                                                }
                                            />
                                        </td>
                                        <td className='border-b px-3 py-2.5'>
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
                                );
                            })}
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
            {showDeleteConfirm && (
                <Modal
                    title={`Delete ${selectedIds.size} meeting${selectedIds.size > 1 ? 's' : ''}`}
                    onClose={() => {
                        if (!batchDeleteMutation.isPending) {
                            setShowDeleteConfirm(false);
                            setBatchDeleteError(null);
                        }
                    }}
                    footer={
                        <>
                            <Button
                                variant='secondary'
                                disabled={batchDeleteMutation.isPending}
                                onClick={() => {
                                    setShowDeleteConfirm(false);
                                    setBatchDeleteError(null);
                                }}
                            >
                                Cancel
                            </Button>
                            <Button
                                variant='danger'
                                disabled={batchDeleteMutation.isPending}
                                onClick={handleConfirmBatchDelete}
                            >
                                {batchDeleteMutation.isPending
                                    ? 'Deleting…'
                                    : 'Delete'}
                            </Button>
                        </>
                    }
                >
                    <div className='space-y-3'>
                        <p className='text-sm text-[var(--text-muted)]'>
                            Are you sure you want to delete {selectedIds.size}{' '}
                            selected meeting{selectedIds.size > 1 ? 's' : ''}?
                            This action will soft-delete them from the
                            dashboard.
                        </p>
                        {batchDeleteError && (
                            <p className='text-xs font-medium text-red-600 dark:text-red-400'>
                                {batchDeleteError}
                            </p>
                        )}
                    </div>
                </Modal>
            )}
        </section>
    );
}
