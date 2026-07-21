import { useEffect, useState, type FormEvent } from 'react';
import { useScheduleMeeting, useUpdateMeeting } from '../../hooks/useMeetingMutations';
import { useProjectMembers } from '../../hooks/useProjectMembers';
import type { Meeting } from '../../domain';
import {
  formatTimeZoneOption,
  getLocalTimeZone,
  listTimeZones,
  nowWallTimeInZone,
  zonedWallTimeToIso,
} from '../../utils/datetime';
import { Button, cn, Icon, Modal, MultiSelectDropdown, SelectDropdown } from '../ui';
import { IssuePicker } from './IssuePicker';

const TIME_ZONE_OPTIONS = listTimeZones().map((zone) => ({
  value: zone,
  label: formatTimeZoneOption(zone),
}));

export interface ScheduleMeetingModalProps {
  isOpen: boolean;
  issueKey?: string;
  projectKey?: string;
  meeting?: Meeting;
  onClose: () => void;
  onSubmitted?: (meetingId: string) => void;
  /** Pass 'embedded' when already rendered inside a Forge platform Modal. */
  chrome?: 'overlay' | 'embedded';
}

type FieldErrors = Partial<Record<'issueKey' | 'title' | 'date' | 'time' | 'form', string>>;

function FieldError({ children }: { children?: string }) {
  return children ? (
    <p className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-300">{children}</p>
  ) : null;
}

export function ScheduleMeetingModal({
  isOpen,
  issueKey,
  projectKey,
  meeting,
  onClose,
  onSubmitted,
  chrome = 'overlay',
}: ScheduleMeetingModalProps) {
  const scheduleMeeting = useScheduleMeeting();
  const updateMeeting = useUpdateMeeting();
  // Issue-context modal payloads normally include the project key, but derive
  // it from the linked issue as a defensive fallback so Jira member lookup is
  // never disabled merely because the optional context field was absent.
  const effectiveProjectKey = projectKey || meeting?.projectKey || issueKey?.split('-')[0];
  const { members, loading: membersLoading } = useProjectMembers(effectiveProjectKey);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [participantIds, setParticipantIds] = useState<string[]>([]);
  const [pickedIssueKey, setPickedIssueKey] = useState('');
  const [timeZone, setTimeZone] = useState(getLocalTimeZone());
  const isEdit = Boolean(meeting);
  const isSubmitting = scheduleMeeting.isPending || updateMeeting.isPending;

  const start = meeting?.scheduledAt ? new Date(meeting.scheduledAt) : null;
  const initialDate = start
    ? `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}-${String(start.getDate()).padStart(2, '0')}`
    : '';
  const initialTime = start
    ? `${String(start.getHours()).padStart(2, '0')}:${String(start.getMinutes()).padStart(2, '0')}`
    : '';
  const [date, setDate] = useState(initialDate);
  const [time, setTime] = useState(initialTime);

  // The form + `date`/`time` state persist across opens when this component
  // stays mounted (see IssueMeetingsPanel's dev-only reused-instance modal) —
  // reset to the target meeting's values (or blank) each time it opens.
  useEffect(() => {
    if (isOpen) {
      setDate(initialDate);
      setTime(initialTime);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, meeting?.id]);

  const nowInZone = nowWallTimeInZone(timeZone);
  const minDate = nowInZone.date;
  const minTime = date === minDate ? nowInZone.time : undefined;

  if (!isOpen) return null;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const title = String(form.get('title') ?? '').trim();
    const resolvedIssueKey = (issueKey ?? pickedIssueKey).trim().toUpperCase();
    const description = String(form.get('description') ?? '').trim();
    const nextErrors: FieldErrors = {};
    if (!resolvedIssueKey)
      nextErrors.issueKey = `Enter an issue key${effectiveProjectKey ? `, e.g. ${effectiveProjectKey}-123` : ''}.`;
    else if (effectiveProjectKey && !resolvedIssueKey.startsWith(`${effectiveProjectKey}-`))
      nextErrors.issueKey = `Issue must belong to project ${effectiveProjectKey}.`;
    if (!title) nextErrors.title = 'Enter a meeting title.';
    if (!date) nextErrors.date = 'Choose a date.';
    if (!time) nextErrors.time = 'Choose a time.';
    const startTime = zonedWallTimeToIso(date, time, timeZone);
    if (startTime && new Date(startTime).getTime() <= Date.now())
      nextErrors.date = 'Choose a future date and time.';
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }

    try {
      const result =
        isEdit && meeting
          ? await updateMeeting.mutateAsync({
              meetingId: meeting.id,
              input: { title, description, startTime },
            })
          : await scheduleMeeting.mutateAsync({
              issueKey: resolvedIssueKey,
              title,
              startTime,
              description,
              participantAccountIds: participantIds,
            });
      onSubmitted?.(result.id);
      onClose();
    } catch (error) {
      setErrors({ form: error instanceof Error ? error.message : 'Could not save the meeting.' });
    }
  };

  return (
    <Modal
      title={isEdit ? 'Edit meeting' : 'Schedule a meeting'}
      description={`${isEdit ? 'Update' : 'Create'} a meeting linked to a Jira issue`}
      size="lg"
      chrome={chrome}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            form="schedule-meeting-form"
            type="submit"
            variant="primary"
            isLoading={isSubmitting}
            leadingIcon={<Icon name={isEdit ? 'check' : 'calendar'} size={16} />}
          >
            {isEdit ? 'Save changes' : 'Schedule meeting'}
          </Button>
        </>
      }
    >
      <form id="schedule-meeting-form" onSubmit={handleSubmit} className="space-y-4">
        {!isEdit && !issueKey && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
              Issue <span className="text-red-500">*</span>
            </span>
            <IssuePicker
              projectKey={effectiveProjectKey ?? ''}
              value={pickedIssueKey}
              autoFocus
              invalid={Boolean(errors.issueKey)}
              onChange={(key) => {
                setPickedIssueKey(key);
                if (errors.issueKey) setErrors((value) => ({ ...value, issueKey: undefined }));
              }}
            />
            <FieldError>{errors.issueKey}</FieldError>
          </label>
        )}
        <label className="block">
          <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
            Title <span className="text-red-500">*</span>
          </span>
          <input
            name="title"
            className="field-control"
            autoFocus={Boolean(isEdit || issueKey)}
            defaultValue={meeting?.title ?? ''}
            placeholder="e.g. Sprint planning sync"
            onChange={() => errors.title && setErrors((value) => ({ ...value, title: undefined }))}
          />
          <FieldError>{errors.title}</FieldError>
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label>
            <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
              Date <span className="text-red-500">*</span>
            </span>
            <div className="relative">
              <Icon
                name="calendar"
                size={14}
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-[var(--text-faint)]"
              />
              <input
                type="date"
                name="date"
                className={cn(
                  'field-control pl-9',
                  errors.date &&
                    'border-red-400 focus:border-red-500 focus:ring-red-500/20 dark:border-red-900/60',
                )}
                min={minDate}
                value={date}
                onChange={(event) => {
                  setDate(event.target.value);
                  if (errors.date) setErrors((value) => ({ ...value, date: undefined }));
                }}
              />
            </div>
            <FieldError>{errors.date}</FieldError>
          </label>
          <label>
            <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
              Time <span className="text-red-500">*</span>
            </span>
            <div className="relative">
              <Icon
                name="clock"
                size={14}
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-[var(--text-faint)]"
              />
              <input
                type="time"
                name="time"
                className={cn(
                  'field-control pl-9',
                  errors.time &&
                    'border-red-400 focus:border-red-500 focus:ring-red-500/20 dark:border-red-900/60',
                )}
                min={minTime}
                value={time}
                onChange={(event) => {
                  setTime(event.target.value);
                  if (errors.time) setErrors((value) => ({ ...value, time: undefined }));
                }}
              />
            </div>
            <FieldError>{errors.time}</FieldError>
          </label>
        </div>
        <label className="block">
          <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">Time zone</span>
          <SelectDropdown
            ariaLabel="Time zone"
            className="w-full"
            value={timeZone}
            options={TIME_ZONE_OPTIONS}
            onChange={setTimeZone}
          />
        </label>
        {!isEdit && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
              Participants
            </span>
            <MultiSelectDropdown
              ariaLabel="Participants"
              placeholder={membersLoading ? 'Loading members…' : 'Select participants'}
              disabled={membersLoading}
              values={participantIds}
              options={members.map((user) => ({
                value: user.accountId,
                label: user.displayName,
              }))}
              onChange={setParticipantIds}
            />
          </label>
        )}
        <label className="block">
          <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
            Description
          </span>
          <textarea
            name="description"
            rows={4}
            className="field-control resize-y"
            defaultValue={meeting?.description ?? ''}
            placeholder="Add context or an agenda…"
          />
        </label>
        <FieldError>{errors.form}</FieldError>
      </form>
    </Modal>
  );
}
