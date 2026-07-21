import { useState, type FormEvent } from 'react';
import { useCreateInstantMeeting } from '../../hooks/useMeetingMutations';
import { useProjectMembers } from '../../hooks/useProjectMembers';
import { Button, Icon, Modal, MultiSelectDropdown } from '../ui';
import { IssuePicker } from './IssuePicker';

export interface StartInstantMeetingModalProps {
  isOpen: boolean;
  projectKey: string;
  onClose: () => void;
  onStarted: (meetingId: string) => void;
}

export function StartInstantMeetingModal({
  isOpen,
  projectKey,
  onClose,
  onStarted,
}: StartInstantMeetingModalProps) {
  const createMeeting = useCreateInstantMeeting();
  const { members, loading: membersLoading } = useProjectMembers(projectKey);
  const [errors, setErrors] = useState<{ issueKey?: string; title?: string; form?: string }>({});
  const [pickedIssueKey, setPickedIssueKey] = useState('');
  const [participantIds, setParticipantIds] = useState<string[]>([]);

  if (!isOpen) return null;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const issueKey = pickedIssueKey.trim().toUpperCase();
    const title = String(form.get('title') ?? '').trim();
    const nextErrors: typeof errors = {};

    if (!issueKey) nextErrors.issueKey = `Enter an issue key, e.g. ${projectKey}-123.`;
    else if (!issueKey.startsWith(`${projectKey}-`))
      nextErrors.issueKey = `Issue must belong to project ${projectKey}.`;
    if (!title) nextErrors.title = 'Enter a meeting title.';
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }

    try {
      const meeting = await createMeeting.mutateAsync({
        issueKey,
        title,
        participantAccountIds: participantIds,
      });
      onClose();
      onStarted(meeting.id);
    } catch (error) {
      setErrors({ form: error instanceof Error ? error.message : 'Could not start the meeting.' });
    }
  };

  return (
    <Modal
      title="Start instant meeting"
      description="Every meeting must be linked to a Jira issue."
      size="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            form="start-instant-meeting-form"
            type="submit"
            variant="primary"
            isLoading={createMeeting.isPending}
            leadingIcon={<Icon name="video" size={16} />}
          >
            Start meeting
          </Button>
        </>
      }
    >
      <form id="start-instant-meeting-form" className="space-y-4" onSubmit={handleSubmit}>
        <label className="block">
          <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
            Issue <span className="text-red-500">*</span>
          </span>
          <IssuePicker
            projectKey={projectKey}
            value={pickedIssueKey}
            autoFocus
            invalid={Boolean(errors.issueKey)}
            onChange={(key) => {
              setPickedIssueKey(key);
              if (errors.issueKey) setErrors((value) => ({ ...value, issueKey: undefined }));
            }}
          />
          {errors.issueKey && (
            <p className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-300">
              {errors.issueKey}
            </p>
          )}
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-bold text-[var(--text-muted)]">
            Title <span className="text-red-500">*</span>
          </span>
          <input
            name="title"
            className="field-control"
            placeholder="e.g. Investigate deployment failure"
            onChange={() => errors.title && setErrors((value) => ({ ...value, title: undefined }))}
          />
          {errors.title && (
            <p className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-300">
              {errors.title}
            </p>
          )}
        </label>
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
        {errors.form && (
          <p className="text-xs font-medium text-red-600 dark:text-red-300">{errors.form}</p>
        )}
      </form>
    </Modal>
  );
}
