import type { Meeting, Participant } from '../../domain';
import { Button, Icon, Modal } from '../ui';
import { LoadingState } from './LoadingState';
import { ParticipantAvatarGroup } from './ParticipantAvatarGroup';
import { MeetingStatusTag } from './StatusTag';

export interface MeetingDetailDialogProps {
    meeting: Meeting | null;
    participants: Participant[];
    isLoading: boolean;
    onClose: () => void;
    /** Pass 'embedded' when Jira's Forge modal supplies the outer chrome. */
    chrome?: 'overlay' | 'embedded';
}

export function MeetingDetailDialog({
    meeting,
    participants,
    isLoading,
    onClose,
    chrome = 'overlay',
}: MeetingDetailDialogProps) {
    if (!meeting) return null;
    return (
        <Modal
            title={meeting.title}
            description={meeting.issueKey}
            chrome={chrome}
            onClose={onClose}
            footer={
                <Button variant='secondary' onClick={onClose}>
                    Close
                </Button>
            }
        >
            {isLoading ? (
                <LoadingState label='Loading meeting details…' />
            ) : (
                <div className='space-y-5'>
                    <div className='flex items-center gap-2'>
                        <MeetingStatusTag status={meeting.status} />
                    </div>
                    {meeting.description && (
                        <p className='text-sm leading-6 text-[var(--text-muted)]'>
                            {meeting.description}
                        </p>
                    )}
                    <dl className='grid gap-3 rounded-2xl bg-[var(--surface-soft)] p-4 text-sm sm:grid-cols-2'>
                        <div>
                            <dt className='text-xs text-[var(--text-faint)]'>
                                Host
                            </dt>
                            <dd className='mt-1 font-semibold text-[var(--text)]'>
                                {meeting.hostName}
                            </dd>
                        </div>
                        <div>
                            <dt className='text-xs text-[var(--text-faint)]'>
                                Starts
                            </dt>
                            <dd className='mt-1 inline-flex items-center gap-1.5 font-semibold text-[var(--text)]'>
                                <Icon name='clock' size={15} />
                                {meeting.scheduledAt || meeting.startedAt
                                    ? new Date(
                                          meeting.scheduledAt
                                              ?? meeting.startedAt
                                              ?? '',
                                      ).toLocaleString()
                                    : 'Not set'}
                            </dd>
                        </div>
                    </dl>
                    <div>
                        <p className='mb-2 text-xs font-bold tracking-wider text-[var(--text-faint)] uppercase'>
                            Participants
                        </p>
                        {participants.length ? (
                            <ParticipantAvatarGroup
                                participants={participants}
                            />
                        ) : (
                            <p className='text-sm text-[var(--text-muted)]'>
                                No participants recorded.
                            </p>
                        )}
                    </div>
                </div>
            )}
        </Modal>
    );
}
