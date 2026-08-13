/**
 * JoinRequestToasts — host-only stack of "{name} is waiting to join" notices.
 *
 * Purely presentational: the queue, its cap and the auto-dismiss timers live
 * in `hooks/useJoinRequestNotifications`. Accept/Decline reuse the room's
 * decision mutations; clicking the toast body (not the buttons) opens the
 * pending sidebar. These notices are not gated by the presence-notification
 * preference.
 */
import type { JoinRequestToast } from '../../hooks/useJoinRequestNotifications';
import { Avatar, Button } from '../ui';

export interface JoinRequestToastsProps {
    toasts: JoinRequestToast[];
    isDeciding?: boolean;
    onAccept: (requestId: string) => void;
    onDecline: (requestId: string) => void;
    onOpenPanel: () => void;
}

export function JoinRequestToasts({
    toasts,
    isDeciding = false,
    onAccept,
    onDecline,
    onOpenPanel,
}: JoinRequestToastsProps) {
    if (toasts.length === 0) return null;

    return (
        <div
            role='status'
            aria-live='polite'
            className='flex w-full flex-col gap-2'
        >
            {toasts.map((toast) => (
                <div
                    key={toast.id}
                    className='pointer-events-auto flex items-center gap-3 rounded-xl border border-[var(--border)] bg-[var(--surface)] px-3 py-2.5 text-sm text-[var(--text)] shadow-card'
                >
                    <button
                        type='button'
                        className='flex min-w-0 flex-1 cursor-pointer items-center gap-3 text-left'
                        onClick={onOpenPanel}
                    >
                        <Avatar
                            name={toast.displayName}
                            src={toast.avatarUrl || undefined}
                            size='sm'
                        />
                        <span className='min-w-0 flex-1 truncate'>
                            {toast.displayName}{' '}
                            <span className='text-[var(--text-muted)]'>
                                is waiting to join
                            </span>
                        </span>
                    </button>
                    <div className='flex shrink-0 gap-1.5'>
                        <Button
                            size='sm'
                            variant='ghost'
                            disabled={isDeciding}
                            onClick={() => onDecline(toast.requestId)}
                        >
                            Decline
                        </Button>
                        <Button
                            size='sm'
                            disabled={isDeciding}
                            onClick={() => onAccept(toast.requestId)}
                        >
                            Accept
                        </Button>
                    </div>
                </div>
            ))}
        </div>
    );
}
