import { Avatar, Icon } from '../../../components/ui';
import { LoadingState } from '../../../components/shared';
import type { Participant } from '../../../domain';

export interface ParticipantListPlaceholderProps {
  participants: Participant[];
  loading: boolean;
}

export function ParticipantListPlaceholder({
  participants,
  loading,
}: ParticipantListPlaceholderProps) {
  return (
    <aside aria-label="Participants" className="surface-card shrink-0 p-4 xl:w-72">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="flex items-center gap-2 font-bold text-[var(--text)]">
          <Icon name="people" size={17} />
          Participants
        </h3>
        <span className="rounded-full bg-[var(--surface-strong)] px-2 py-0.5 text-[10px] font-bold text-[var(--text-muted)]">
          {participants.length}
        </span>
      </div>
      {loading ? (
        <LoadingState label="Loading participants…" />
      ) : (
        <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
          {participants.map((participant) => (
            <li
              key={participant.accountId}
              className="flex items-center gap-3 rounded-xl p-2 transition hover:bg-[var(--surface-soft)]"
            >
              <Avatar name={participant.displayName} size="sm" />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold text-[var(--text)]">
                {participant.displayName}
              </span>
              {participant.role === 'HOST' && (
                <span className="rounded-full bg-brand-100 px-2 py-0.5 text-[9px] font-bold text-brand-700 uppercase dark:bg-brand-500/15 dark:text-brand-300">
                  Host
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}
