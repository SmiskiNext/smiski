/**
 * ParticipantAvatarGroup — overlapping avatar stack for a meeting's
 * participants.
 */
import type { Participant } from '../../domain';
import { Avatar } from '../ui';

export interface ParticipantAvatarGroupProps {
    participants: Participant[];
    avatarUrlByAccountId?: Record<string, string | undefined>;
    maxCount?: number;
}

export function ParticipantAvatarGroup({
    participants,
    avatarUrlByAccountId,
    maxCount = 5,
}: ParticipantAvatarGroupProps) {
    const visible = participants.slice(0, maxCount);
    const overflow = participants.length - visible.length;
    return (
        <div
            className='flex -space-x-2'
            role='img'
            aria-label={`${participants.length} participants`}
        >
            {visible.map((participant) => (
                <Avatar
                    key={participant.accountId}
                    name={participant.displayName}
                    src={avatarUrlByAccountId?.[participant.accountId]}
                    size='sm'
                />
            ))}
            {overflow > 0 && (
                <span className='flex size-8 items-center justify-center rounded-full bg-[var(--surface-strong)] text-[10px] font-bold text-[var(--text-muted)] ring-2 ring-[var(--surface)]'>
                    +{overflow}
                </span>
            )}
        </div>
    );
}
