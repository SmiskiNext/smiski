import { useEffect, useRef, useState } from 'react';
import { useMeeting } from '../../../hooks/useMeeting';
import { useMeetingParticipants } from '../../../hooks/useMeetingParticipants';
import { LoadingState, MeetingStatusTag } from '../../../components/shared';
import { Avatar, Button, Icon } from '../../../components/ui';

export interface MeetingDetailPanelProps {
  meetingId: string;
  focus?: 'details' | 'history';
  onClose: () => void;
}

export function MeetingDetailPanel({
  meetingId,
  focus = 'details',
  onClose,
}: MeetingDetailPanelProps) {
  const { meeting, loading: meetingLoading } = useMeeting(meetingId);
  const { participants, loading: participantsLoading } = useMeetingParticipants(
    meetingId,
    meeting?.projectKey,
  );
  const [isVisible, setIsVisible] = useState(false);
  const historyRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const frame = requestAnimationFrame(() => setIsVisible(true));
    const onKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKeyDown);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose]);

  useEffect(() => {
    if (focus === 'history' && meeting) historyRef.current?.scrollIntoView({ block: 'start' });
  }, [focus, meeting]);

  return (
    <div className="fixed inset-0 z-50" role="presentation">
      <button
        aria-label="Close meeting details"
        onClick={onClose}
        className={`absolute inset-0 bg-slate-950/40 transition-opacity duration-150 ${isVisible ? 'opacity-100' : 'opacity-0'}`}
      />
      <aside
        aria-label="Meeting detail"
        className={`scrollbar-subtle absolute top-0 right-0 bottom-0 w-full max-w-md overflow-y-auto border-l bg-[var(--surface)] shadow-panel transition-transform duration-200 ease-out ${isVisible ? 'translate-x-0' : 'translate-x-full'}`}
      >
        <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b bg-[var(--surface)]/96 px-5 backdrop-blur">
          <div>
            <h2 className="text-sm font-semibold text-[var(--text)]">Meeting details</h2>
            <p className="text-[11px] text-[var(--text-faint)]">
              {meeting?.issueKey ?? 'Project meeting'}
            </p>
          </div>
          <Button
            size="icon"
            variant="ghost"
            className="size-8 min-h-0 rounded-md"
            aria-label="Close"
            onClick={onClose}
          >
            <Icon name="x" size={17} />
          </Button>
        </header>
        {meetingLoading || !meeting ? (
          <div className="p-5">
            <LoadingState label="Loading meeting…" />
          </div>
        ) : (
          <div className="space-y-6 p-5">
            <section>
              <div className="mb-2">
                <MeetingStatusTag status={meeting.status} />
              </div>
              <h3 className="text-lg font-semibold tracking-tight text-[var(--text)]">
                {meeting.title}
              </h3>
              {meeting.description && (
                <p className="mt-2 text-sm leading-6 text-[var(--text-muted)]">
                  {meeting.description}
                </p>
              )}
            </section>
            <dl className="divide-y border text-sm">
              <div className="grid grid-cols-[100px_1fr] gap-3 px-3 py-2.5">
                <dt className="text-[var(--text-faint)]">Project</dt>
                <dd className="font-medium text-[var(--text)]">{meeting.projectKey}</dd>
              </div>
              <div className="grid grid-cols-[100px_1fr] gap-3 px-3 py-2.5">
                <dt className="text-[var(--text-faint)]">Issue</dt>
                <dd className="font-medium text-brand-700 dark:text-brand-300">
                  {meeting.issueKey ?? 'Not linked'}
                </dd>
              </div>
              <div className="grid grid-cols-[100px_1fr] gap-3 px-3 py-2.5">
                <dt className="text-[var(--text-faint)]">Creator</dt>
                <dd className="font-medium text-[var(--text)]">{meeting.creatorName}</dd>
              </div>
              <div className="grid grid-cols-[100px_1fr] gap-3 px-3 py-2.5">
                <dt className="text-[var(--text-faint)]">Host</dt>
                <dd className="font-medium text-[var(--text)]">{meeting.hostName}</dd>
              </div>
            </dl>
            <section>
              <h3 className="mb-3 flex items-center gap-2 text-xs font-semibold text-[var(--text)]">
                <Icon name="people" size={15} />
                Participants{' '}
                <span className="text-[var(--text-faint)]">{meeting.participantCount}</span>
              </h3>
              {participantsLoading ? (
                <LoadingState label="Loading participants…" />
              ) : participants.length ? (
                <ul className="divide-y border">
                  {participants.map((participant) => (
                    <li key={participant.accountId} className="flex items-center gap-3 px-3 py-2">
                      <Avatar name={participant.displayName} size="sm" />
                      <span className="min-w-0 flex-1 truncate text-sm text-[var(--text)]">
                        {participant.displayName}
                      </span>
                      <span className="text-[10px] font-medium text-[var(--text-faint)]">
                        {participant.role === 'HOST' ? 'Host' : 'Participant'}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-[var(--text-muted)]">No participant activity.</p>
              )}
            </section>
            <section ref={historyRef} className={focus === 'history' ? 'scroll-mt-16' : ''}>
              <h3 className="mb-3 flex items-center gap-2 text-xs font-semibold text-[var(--text)]">
                <Icon name="history" size={15} />
                Meeting history
              </h3>
              <ol className="border-l pl-4 text-sm">
                {meeting.scheduledAt && (
                  <li className="relative pb-4">
                    <span className="absolute top-1.5 -left-[20px] size-2 rounded-full bg-blue-500" />
                    <p className="font-medium text-[var(--text)]">Scheduled</p>
                    <time className="text-xs text-[var(--text-faint)]">
                      {new Date(meeting.scheduledAt).toLocaleString()}
                    </time>
                  </li>
                )}
                {meeting.startedAt && (
                  <li className="relative pb-4">
                    <span className="absolute top-1.5 -left-[20px] size-2 rounded-full bg-emerald-500" />
                    <p className="font-medium text-[var(--text)]">Started</p>
                    <time className="text-xs text-[var(--text-faint)]">
                      {new Date(meeting.startedAt).toLocaleString()}
                    </time>
                  </li>
                )}
                {meeting.endedAt && (
                  <li className="relative">
                    <span className="absolute top-1.5 -left-[20px] size-2 rounded-full bg-slate-400" />
                    <p className="font-medium text-[var(--text)]">Ended</p>
                    <time className="text-xs text-[var(--text-faint)]">
                      {new Date(meeting.endedAt).toLocaleString()}
                    </time>
                  </li>
                )}
                {!meeting.scheduledAt && !meeting.startedAt && !meeting.endedAt && (
                  <li className="text-[var(--text-muted)]">No history available.</li>
                )}
              </ol>
            </section>
          </div>
        )}
      </aside>
    </div>
  );
}
