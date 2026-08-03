import { Button } from '../../../components/ui';
import {
    useAcceptJoinRequests,
    useDeclineJoinRequests,
    usePendingJoinRequests,
} from '../../../hooks/useJoinRequests';

export interface PendingJoinRequestsPanelProps {
    meetingId: string;
}

export function PendingJoinRequestsPanel({
    meetingId,
}: PendingJoinRequestsPanelProps) {
    const pending = usePendingJoinRequests(meetingId);
    const accept = useAcceptJoinRequests();
    const decline = useDeclineJoinRequests();
    const requests = pending.data?.requests ?? [];

    if (pending.isLoading || requests.length === 0) return null;

    const isDeciding = accept.isPending || decline.isPending;
    return (
        <section className='mb-4 rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4'>
            <div className='mb-3 flex items-center justify-between'>
                <div>
                    <h2 className='text-sm font-semibold text-[var(--text)]'>
                        Waiting to join
                    </h2>
                    <p className='text-xs text-[var(--text-muted)]'>
                        Approve participants before they receive room access.
                    </p>
                </div>
                <span className='rounded-full bg-[var(--surface-strong)] px-2 py-1 text-xs font-medium'>
                    {pending.data?.total ?? requests.length}
                </span>
            </div>
            <div className='space-y-2'>
                {requests.map((request) => (
                    <div
                        key={request.requestId}
                        className='flex items-center justify-between gap-3 rounded-md border border-[var(--border)] p-3'
                    >
                        <div className='min-w-0'>
                            <p className='truncate text-sm font-medium text-[var(--text)]'>
                                {request.displayName}
                            </p>
                            <p className='truncate text-xs text-[var(--text-muted)]'>
                                {request.accountId}
                            </p>
                        </div>
                        <div className='flex shrink-0 gap-2'>
                            <Button
                                size='sm'
                                variant='ghost'
                                disabled={isDeciding}
                                onClick={() =>
                                    decline.mutate({
                                        meetingId,
                                        requestIds: [request.requestId],
                                    })
                                }
                            >
                                Decline
                            </Button>
                            <Button
                                size='sm'
                                disabled={isDeciding}
                                onClick={() =>
                                    accept.mutate({
                                        meetingId,
                                        requestIds: [request.requestId],
                                    })
                                }
                            >
                                Accept
                            </Button>
                        </div>
                    </div>
                ))}
            </div>
        </section>
    );
}
