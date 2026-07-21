import { useState } from 'react';
import { Modal as ForgeModal } from '@forge/bridge';
import { useCreateInstantMeeting } from '../../hooks/useMeetingMutations';
import { useHostConflict } from '../../hooks/useHostConflict';
import { ActiveMeetingWarningDialog, InlineFeedback } from '../../components/shared';
import { Button, Icon } from '../../components/ui';
import {
  ACTIVE_MEETING_WARNING_MODAL_KIND,
  type ActiveMeetingWarningModalContext,
  type ActiveMeetingWarningModalResult,
} from '../../utils/issuePanelModalContext';

export interface StartInstantMeetingButtonProps {
  issueKey: string;
  disabled?: boolean;
  onStarted?: (meetingId: string) => void;
  className?: string;
}

export function StartInstantMeetingButton({
  issueKey,
  disabled,
  onStarted,
  className,
}: StartInstantMeetingButtonProps) {
  const [isConfirmOpen, setConfirmOpen] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const { conflictingMeeting } = useHostConflict(issueKey);
  const createInstantMeeting = useCreateInstantMeeting();

  const start = () =>
    createInstantMeeting.mutate(
      { issueKey, title: `Instant meeting — ${issueKey}` },
      {
        onSuccess: (meeting) => {
          setFeedback('Instant meeting started.');
          onStarted?.(meeting.id);
        },
      },
    );

  const handleClick = () => {
    if (!conflictingMeeting) {
      start();
      return;
    }

    if (import.meta.env.DEV) {
      setConfirmOpen(true);
      return;
    }

    const context: ActiveMeetingWarningModalContext = {
      kind: ACTIVE_MEETING_WARNING_MODAL_KIND,
      conflictingMeeting,
    };
    new ForgeModal({
      context,
      size: 'medium',
      onClose: (result: ActiveMeetingWarningModalResult | undefined) => {
        if (result?.confirmed) start();
      },
    }).open();
  };

  return (
    <>
      <Button
        variant="primary"
        size="sm"
        className={className}
        leadingIcon={<Icon name="video" size={15} />}
        onClick={handleClick}
        disabled={disabled}
        isLoading={createInstantMeeting.isPending}
      >
        Start instant
      </Button>
      {feedback && (
        <InlineFeedback
          appearance="success"
          message={feedback}
          onDismiss={() => setFeedback(null)}
        />
      )}
      {import.meta.env.DEV && isConfirmOpen && conflictingMeeting && (
        <ActiveMeetingWarningDialog
          conflictingMeeting={conflictingMeeting}
          onClose={() => setConfirmOpen(false)}
          onConfirm={() => {
            setConfirmOpen(false);
            start();
          }}
        />
      )}
    </>
  );
}
