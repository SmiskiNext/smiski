import type { Meeting } from '../../domain';
import { Button, Icon, Modal } from '../ui';

export interface ActiveMeetingWarningDialogProps {
  conflictingMeeting: Meeting;
  onClose: () => void;
  onConfirm: () => void;
  chrome?: 'overlay' | 'embedded';
}

/**
 * Shared confirmation content for standalone development and the Forge modal
 * iframe. Keeping the decision UI in one component prevents the two surfaces
 * from drifting apart as the workflow evolves.
 */
export function ActiveMeetingWarningDialog({
  conflictingMeeting,
  onClose,
  onConfirm,
  chrome = 'overlay',
}: ActiveMeetingWarningDialogProps) {
  return (
    <Modal
      title="Active meeting detected"
      description="You are already hosting another meeting."
      chrome={chrome}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="warning"
            leadingIcon={<Icon name="video" size={16} />}
            onClick={onConfirm}
          >
            Start anyway
          </Button>
        </>
      }
    >
      <div className="flex gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200">
        <Icon name="alert" className="mt-0.5 shrink-0" />
        <p>
          You are hosting <strong>{conflictingMeeting.title}</strong> on{' '}
          <strong>{conflictingMeeting.issueKey}</strong>. Starting this meeting will keep both rooms
          active.
        </p>
      </div>
    </Modal>
  );
}
