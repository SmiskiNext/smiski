/**
 * ParticipantPresenceToasts — bottom-right stack of "{name} joined" /
 * "{name} left" notices for the meeting room.
 *
 * Purely presentational: the queue, its cap and the auto-dismiss timers live in
 * `hooks/useParticipantPresenceNotifications`. Fixed to the viewport so the
 * notices float over the room without shifting the video area, and
 * `pointer-events-none` on the container keeps that overlay from swallowing
 * clicks meant for the control bar underneath — each toast re-enables pointer
 * events for its own close button.
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
            className='pointer-events-none fixed right-4 bottom-4 z-50 flex w-72 flex-col gap-2'
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
