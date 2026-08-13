/**
 * ParticipantPresenceToasts — bottom-right stack of "{name} joined" /
 * "{name} left" notices for the meeting room.
 *
 * Purely presentational: the queue, its cap and the auto-dismiss timers live in
 * `hooks/useParticipantPresenceNotifications`. The room owns the shared
 * bottom-right overlay so these notices can stack with join-request toasts
 * without overlapping. Each toast re-enables pointer events for its close
 * button.
 */
import type { ParticipantPresenceToast } from '../../hooks/useParticipantPresenceNotifications';
import { Avatar, Button, Icon } from '../ui';

export interface ParticipantPresenceToastsProps {
    toasts: ParticipantPresenceToast[];
    onDismiss: (id: string) => void;
}

export function ParticipantPresenceToasts({
    toasts,
    onDismiss,
}: ParticipantPresenceToastsProps) {
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
                    <Avatar name={toast.displayName} size='sm' />
                    <span className='min-w-0 flex-1 truncate'>
                        {toast.displayName}{' '}
                        <span className='text-[var(--text-muted)]'>
                            {toast.kind === 'joined' ? 'joined' : 'left'}
                        </span>
                    </span>
                    <Button
                        variant='ghost'
                        size='icon'
                        className='size-7 min-h-0'
                        aria-label='Dismiss'
                        onClick={() => onDismiss(toast.id)}
                    >
                        <Icon name='x' size={15} />
                    </Button>
                </div>
            ))}
        </div>
    );
}
