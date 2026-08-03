import { useCallback, useEffect, useMemo, useState } from 'react';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import { useCurrentUser } from '../../context/CurrentUserContext';
import type { Meeting, MeetingInviteeStatus } from '../../domain';
import {
    useAddMeetingInvitees,
    useMeetingInvitees,
    useRemoveMeetingInvitees,
} from '../../hooks/useMeetingInvitees';
import { Avatar, Button, cn, Icon } from '../ui';
import { InlineFeedback } from './InlineFeedback';
import { LoadingState } from './LoadingState';
import { WorkspaceUserPicker } from './WorkspaceUserPicker';

export interface MeetingInviteeManagerProps {
    meeting: Meeting;
}

interface Feedback {
    appearance: 'success' | 'error';
    message: string;
}

const STATUS_LABELS: Record<MeetingInviteeStatus, string> = {
    NEEDS_ACTION: 'Awaiting response',
    ACCEPTED: 'Accepted',
    DECLINED: 'Declined',
    TENTATIVE: 'Tentative',
};

const STATUS_STYLES: Record<MeetingInviteeStatus, string> = {
    NEEDS_ACTION:
        'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200',
    ACCEPTED:
        'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-200',
    DECLINED: 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-200',
    TENTATIVE:
        'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-200',
};

function errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
}

/** Host-only invitee management for a SCHEDULED meeting. */
export function MeetingInviteeManager({ meeting }: MeetingInviteeManagerProps) {
    const currentUser = useCurrentUser();
    const { invitees, loading, error } = useMeetingInvitees(meeting.id);
    const addInvitees = useAddMeetingInvitees();
    const removeInvitees = useRemoveMeetingInvitees();
    const [newInvitees, setNewInvitees] = useState<WorkspaceUser[]>([]);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [feedback, setFeedback] = useState<Feedback | null>(null);
    const dismissFeedback = useCallback(() => setFeedback(null), []);

    const excludedAccountIds = useMemo(
        () => [meeting.hostId, ...invitees.map((invitee) => invitee.accountId)],
        [invitees, meeting.hostId],
    );
    const canManage = meeting.hostId === currentUser.accountId;
    const mutationPending = addInvitees.isPending || removeInvitees.isPending;
    const managementDisabled = mutationPending || loading || Boolean(error);

    // biome-ignore lint/correctness/useExhaustiveDependencies: reset local management state when the selected meeting changes
    useEffect(() => {
        setNewInvitees([]);
        setSelectedIds(new Set());
        setFeedback(null);
    }, [meeting.id]);

    if (meeting.status !== 'SCHEDULED') return null;

    const handleAdd = async () => {
        if (newInvitees.length === 0) return;
        setFeedback(null);
        try {
            await addInvitees.mutateAsync({
                meetingId: meeting.id,
                invitees: newInvitees.map((invitee) => ({
                    accountId: invitee.accountId,
                    displayName: invitee.displayName,
                    email: invitee.email,
                })),
            });
            setNewInvitees([]);
            setFeedback({
                appearance: 'success',
                message: 'Invitees added.',
            });
        } catch (mutationError) {
            setFeedback({
                appearance: 'error',
                message: errorMessage(mutationError, 'Could not add invitees.'),
            });
        }
    };

    const handleRemove = async () => {
        if (selectedIds.size === 0) return;
        setFeedback(null);
        try {
            await removeInvitees.mutateAsync({
                meetingId: meeting.id,
                inviteeIds: [...selectedIds],
            });
            setSelectedIds(new Set());
            setFeedback({
                appearance: 'success',
                message: 'Invitees removed.',
            });
        } catch (mutationError) {
            setFeedback({
                appearance: 'error',
                message: errorMessage(
                    mutationError,
                    'Could not remove invitees.',
                ),
            });
        }
    };

    const toggleSelected = (inviteeId: string) => {
        setSelectedIds((current) => {
            const next = new Set(current);
            if (next.has(inviteeId)) next.delete(inviteeId);
            else next.add(inviteeId);
            return next;
        });
    };

    return (
        <section aria-label='Meeting invitees'>
            <div className='mb-3 flex items-center justify-between gap-3'>
                <h3 className='flex items-center gap-2 text-xs font-semibold text-[var(--text)]'>
                    <Icon name='people' size={15} />
                    Invitees
                    <span className='text-[var(--text-faint)]'>
                        {invitees.length}
                    </span>
                </h3>
                {canManage && selectedIds.size > 0 && (
                    <Button
                        size='sm'
                        variant='danger'
                        isLoading={removeInvitees.isPending}
                        disabled={managementDisabled}
                        onClick={() => void handleRemove()}
                    >
                        Remove selected ({selectedIds.size})
                    </Button>
                )}
            </div>

            {canManage && (
                <div className='mb-3 space-y-2 border bg-[var(--surface-soft)] p-3'>
                    <WorkspaceUserPicker
                        value={newInvitees}
                        onChange={setNewInvitees}
                        excludedAccountIds={excludedAccountIds}
                        requireEmail
                        disabled={managementDisabled}
                        ariaLabel='Add meeting invitees'
                    />
                    <div className='flex justify-end'>
                        <Button
                            size='sm'
                            variant='primary'
                            leadingIcon={<Icon name='plus' size={14} />}
                            isLoading={addInvitees.isPending}
                            disabled={
                                managementDisabled || newInvitees.length === 0
                            }
                            onClick={() => void handleAdd()}
                        >
                            Add invitees
                        </Button>
                    </div>
                </div>
            )}

            {feedback && (
                <InlineFeedback
                    appearance={feedback.appearance}
                    message={feedback.message}
                    onDismiss={dismissFeedback}
                />
            )}

            {loading ? (
                <LoadingState label='Loading invitees…' />
            ) : error ? (
                <p className='text-sm text-red-600 dark:text-red-300'>
                    {error.message}
                </p>
            ) : invitees.length ? (
                <ul className='divide-y border'>
                    {invitees.map((invitee) => (
                        <li
                            key={invitee.id}
                            className='flex items-center gap-3 px-3 py-2.5'
                        >
                            {canManage && (
                                <input
                                    type='checkbox'
                                    checked={selectedIds.has(invitee.id)}
                                    disabled={managementDisabled}
                                    aria-label={`Select ${invitee.displayName}`}
                                    onChange={() => toggleSelected(invitee.id)}
                                    className='size-4 accent-brand-600'
                                />
                            )}
                            <Avatar name={invitee.displayName} size='sm' />
                            <div className='min-w-0 flex-1'>
                                <p className='truncate text-sm font-medium text-[var(--text)]'>
                                    {invitee.displayName}
                                </p>
                                <p className='truncate text-xs text-[var(--text-faint)]'>
                                    {invitee.email}
                                </p>
                            </div>
                            <span
                                className={cn(
                                    'shrink-0 rounded-full px-2 py-1 text-[10px] font-semibold',
                                    STATUS_STYLES[invitee.status],
                                )}
                            >
                                {STATUS_LABELS[invitee.status]}
                            </span>
                        </li>
                    ))}
                </ul>
            ) : (
                <p className='text-sm text-[var(--text-muted)]'>
                    No invitees yet.
                </p>
            )}
        </section>
    );
}
