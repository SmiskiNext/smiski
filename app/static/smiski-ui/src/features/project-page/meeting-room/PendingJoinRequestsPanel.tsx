import { LoadingState } from '../../../components/shared/LoadingState';
import { Avatar, Button, Icon } from '../../../components/ui';
import type { PendingJoinRequest } from '../../../domain';

export interface PendingJoinRequestsPanelProps {
    requests: PendingJoinRequest[];
    total: number;
    isLoading: boolean;
    isDeciding: boolean;
    onAccept: (requestId: string) => void;
    onDecline: (requestId: string) => void;
    onClose?: () => void;
}

export function PendingJoinRequestsPanel({
    requests,
    total,
    isLoading,
    isDeciding,
    onAccept,
    onDecline,
    onClose,
}: PendingJoinRequestsPanelProps) {
    return (
        <aside
            aria-label='Waiting to join'
            className='surface-card flex shrink-0 flex-col p-4 xl:w-72'
        >
            <div className='mb-4 flex items-center justify-between gap-2'>
                <h3 className='flex items-center gap-2 font-bold text-[var(--text)]'>
                    <Icon name='userPlus' size={17} />
                    Waiting to join
                </h3>
                <div className='flex items-center gap-2'>
                    <span className='rounded-full bg-[var(--surface-strong)] px-2 py-0.5 text-[10px] font-bold text-[var(--text-muted)]'>
                        {total}
                    </span>
                    {onClose && (
                        <Button
                            variant='ghost'
                            size='icon'
                            className='size-7 min-h-0'
                            aria-label='Close pending requests'
                            onClick={onClose}
                        >
                            <Icon name='x' size={15} />
                        </Button>
                    )}
                </div>
            </div>
            {isLoading ? (
                <LoadingState label='Loading join requests…' />
            ) : requests.length === 0 ? (
                <p className='px-2 py-5 text-sm text-[var(--text-muted)]'>
                    No one is waiting to join.
                </p>
            ) : (
                <ul className='grid gap-2 sm:grid-cols-2 xl:grid-cols-1'>
                    {requests.map((request) => (
                        <li
                            key={request.requestId}
                            className='flex flex-col gap-3 rounded-xl p-2 transition hover:bg-[var(--surface-soft)]'
                        >
                            <div className='flex min-w-0 items-center gap-3'>
                                <Avatar
                                    name={request.displayName}
                                    src={request.avatarUrl || undefined}
                                    size='sm'
                                />
                                <span className='min-w-0 flex-1 truncate text-sm font-semibold text-[var(--text)]'>
                                    {request.displayName}
                                </span>
                            </div>
                            <div className='flex gap-2'>
                                <Button
                                    size='sm'
                                    variant='ghost'
                                    className='flex-1'
                                    disabled={isDeciding}
                                    onClick={() => onDecline(request.requestId)}
                                >
                                    Decline
                                </Button>
                                <Button
                                    size='sm'
                                    className='flex-1'
                                    disabled={isDeciding}
                                    onClick={() => onAccept(request.requestId)}
                                >
                                    Accept
                                </Button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </aside>
    );
}
