'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Badge } from '@/components/ui/badge.tsx';
import { Button } from '@/components/ui/button.tsx';
import type { MeetingManagementInviteeListResponse } from '@/generated/types.gen.ts';
import type { UseInviteManagementReturn } from './use-invite-management.ts';

const INVITEE_CAP = 10;

type AddInviteeFormValues = {
    email: string;
};

function buildAddInviteeSchema(invalidEmailMessage: string) {
    return z.object({
        email: z.string().email(invalidEmailMessage),
    });
}

type InviteStatusBadgeProps = {
    status: MeetingManagementInviteeListResponse['status'];
    labels: { pending: string; accepted: string; declined: string };
};

function InviteStatusBadge({ status, labels }: InviteStatusBadgeProps) {
    if (status === 'ACCEPTED') {
        return (
            <Badge className='border-transparent bg-success-subtle text-success'>
                {labels.accepted}
            </Badge>
        );
    }
    if (status === 'DECLINED') {
        return (
            <Badge className='border-transparent bg-error-subtle text-error-dark'>
                {labels.declined}
            </Badge>
        );
    }
    return (
        <Badge className='border-transparent bg-warning-subtle text-warning'>
            {labels.pending}
        </Badge>
    );
}

type TokenStatusBadgeProps = {
    tokenStatus: string | undefined;
    labels: {
        pending: string;
        used: string;
        revoked: string;
        expired: string;
    };
};

function TokenStatusBadge({ tokenStatus, labels }: TokenStatusBadgeProps) {
    const labelMap: Record<string, string> = {
        PENDING: labels.pending,
        USED: labels.used,
        REVOKED: labels.revoked,
        EXPIRED: labels.expired,
    };
    const label = tokenStatus ? (labelMap[tokenStatus] ?? tokenStatus) : '';
    return (
        <Badge variant='outline' className='text-xs'>
            {label}
        </Badge>
    );
}

function resolveInviteeDisplay(
    invitee: MeetingManagementInviteeListResponse,
): string {
    return invitee.email ?? invitee.displayName ?? invitee.inviteeId ?? '';
}

function showResendButton(tokenStatus: string | undefined): boolean {
    return (
        tokenStatus === 'PENDING'
        || tokenStatus === 'EXPIRED'
        || tokenStatus === 'REVOKED'
    );
}

function showRevokeButton(
    status: MeetingManagementInviteeListResponse['status'],
    tokenStatus: string | undefined,
): boolean {
    return status === 'PENDING' && tokenStatus === 'PENDING';
}

type InviteeRowProps = {
    invitee: MeetingManagementInviteeListResponse;
    rowState: { isLoading: boolean; error: string | null } | undefined;
    onResend: (inviteeId: string) => void;
    onRevoke: (inviteeId: string) => void;
    labels: {
        resend: string;
        revoke: string;
        inviteStatus: { pending: string; accepted: string; declined: string };
        tokenStatus: {
            pending: string;
            used: string;
            revoked: string;
            expired: string;
        };
    };
};

function InviteeRow({
    invitee,
    rowState,
    onResend,
    onRevoke,
    labels,
}: InviteeRowProps) {
    const inviteeId = invitee.inviteeId ?? '';
    const displayText = resolveInviteeDisplay(invitee);
    const isActionLoading = rowState?.isLoading ?? false;

    return (
        <div className='flex flex-col gap-1'>
            <div className='flex flex-wrap items-center justify-between gap-2 py-2'>
                <div className='flex flex-wrap items-center gap-2'>
                    <span className='text-sm text-text-dark'>
                        {displayText}
                    </span>
                    <InviteStatusBadge
                        labels={labels.inviteStatus}
                        status={invitee.status}
                    />
                    <TokenStatusBadge
                        labels={labels.tokenStatus}
                        tokenStatus={invitee.tokenStatus}
                    />
                </div>
                <div className='flex gap-2'>
                    {showResendButton(invitee.tokenStatus) && (
                        <Button
                            disabled={isActionLoading}
                            onClick={() => onResend(inviteeId)}
                            size='sm'
                            type='button'
                            variant='outline'
                        >
                            {labels.resend}
                        </Button>
                    )}
                    {showRevokeButton(invitee.status, invitee.tokenStatus) && (
                        <Button
                            disabled={isActionLoading}
                            onClick={() => onRevoke(inviteeId)}
                            size='sm'
                            type='button'
                            variant='outline'
                        >
                            {labels.revoke}
                        </Button>
                    )}
                </div>
            </div>
            {rowState?.error && (
                <p className='text-xs text-error' role='alert'>
                    {rowState.error}
                </p>
            )}
        </div>
    );
}

type InviteManagementSectionProps = {
    listState: UseInviteManagementReturn['listState'];
    addState: UseInviteManagementReturn['addState'];
    rowStates: UseInviteManagementReturn['rowStates'];
    onAddInvitee: (email: string) => Promise<boolean>;
    onResend: (inviteeId: string) => Promise<void>;
    onRevoke: (inviteeId: string) => Promise<void>;
};

/**
 * Presentational section for invite management within the meeting detail sheet.
 * All data-fetching and mutation state is owned by the parent via props.
 */
export function InviteManagementSection({
    listState,
    addState,
    rowStates,
    onAddInvitee,
    onResend,
    onRevoke,
}: InviteManagementSectionProps) {
    const t = useTranslations('inviteManagement');

    const schema = buildAddInviteeSchema(t('validation.invalidEmail'));

    const {
        register,
        handleSubmit,
        reset,
        formState: { errors },
    } = useForm<AddInviteeFormValues>({
        resolver: zodResolver(schema),
    });

    async function handleAddSubmit(values: AddInviteeFormValues) {
        const succeeded = await onAddInvitee(values.email.trim());
        if (succeeded) {
            reset();
        }
    }

    const invitees = listState.phase === 'SUCCESS' ? listState.invitees : [];
    const atCap = invitees.length >= INVITEE_CAP;

    return (
        <div>
            <p className='mb-2 text-xs font-medium uppercase tracking-wide text-text-subtle'>
                {t('sectionTitle')}
            </p>

            {listState.phase === 'LOADING' && (
                <div className='space-y-2'>
                    {[1, 2].map((i) => (
                        <div
                            className='h-8 animate-pulse rounded-md bg-surface-input'
                            key={i}
                        />
                    ))}
                </div>
            )}

            {listState.phase === 'ERROR' && (
                <p className='text-sm text-error' role='alert'>
                    {t('errorState')}
                </p>
            )}

            {listState.phase === 'SUCCESS' && invitees.length === 0 && (
                <p className='text-sm text-text-subtle'>{t('emptyState')}</p>
            )}

            {listState.phase === 'SUCCESS' && invitees.length > 0 && (
                <div className='divide-y divide-border-muted rounded-xl border border-border-card px-4'>
                    {invitees.map((invitee) => (
                        <InviteeRow
                            key={invitee.inviteeId}
                            invitee={invitee}
                            labels={{
                                resend: t('actions.resend'),
                                revoke: t('actions.revoke'),
                                inviteStatus: {
                                    pending: t('status.pending'),
                                    accepted: t('status.accepted'),
                                    declined: t('status.declined'),
                                },
                                tokenStatus: {
                                    pending: t('tokenStatus.pending'),
                                    used: t('tokenStatus.used'),
                                    revoked: t('tokenStatus.revoked'),
                                    expired: t('tokenStatus.expired'),
                                },
                            }}
                            onResend={onResend}
                            onRevoke={onRevoke}
                            rowState={
                                invitee.inviteeId
                                    ? rowStates[invitee.inviteeId]
                                    : undefined
                            }
                        />
                    ))}
                </div>
            )}

            {!atCap && (
                <form
                    className='mt-3 flex flex-col gap-1'
                    onSubmit={handleSubmit(handleAddSubmit)}
                >
                    <div className='flex gap-2'>
                        <input
                            className='h-10 flex-1 rounded-xl border border-border-card bg-surface px-3 text-sm text-text-dark outline-none ring-transparent transition focus:ring-2 focus:ring-primary'
                            placeholder={t('addForm.placeholder')}
                            type='email'
                            {...register('email')}
                        />
                        <Button
                            disabled={addState.isLoading}
                            size='sm'
                            type='submit'
                        >
                            {t('addForm.button')}
                        </Button>
                    </div>
                    {errors.email && (
                        <p className='text-xs text-error' role='alert'>
                            {errors.email.message}
                        </p>
                    )}
                    {addState.error && (
                        <p className='text-xs text-error' role='alert'>
                            {addState.error}
                        </p>
                    )}
                </form>
            )}

            {atCap && (
                <p className='mt-2 text-xs text-text-subtle'>
                    {t('capReached')}
                </p>
            )}
        </div>
    );
}
