import { useState } from 'react';
import {
    ActiveMeetingWarningDialog,
    ConfirmMeetingActionDialog,
    ErrorState,
    InlineFeedback,
    LoadingState,
    NoPermissionState,
    ScheduleMeetingModal,
    StartInstantMeetingModal,
} from '../../../components/shared';
import type { Meeting, MeetingAction } from '../../../domain';
import { useConfirmMeetingAction } from '../../../hooks/useConfirmMeetingAction';
import { useHostConflictGuard } from '../../../hooks/useHostConflictGuard';
import { useStartMeeting } from '../../../hooks/useMeetingMutations';
import { useMeetingPermissions } from '../../../hooks/useMeetingPermission';
import { useProjectMeetings } from '../../../hooks/useProjectMeetings';
import { DashboardHeader } from './DashboardHeader';
import { EditMeetingModal } from './EditMeetingModal';
import { MeetingDetailPanel } from './MeetingDetailPanel';
import { MeetingListTable } from './MeetingListTable';
import {
    DEFAULT_PROJECT_MEETING_SORT,
    type MeetingFilterValue,
    SearchAndFilterBar,
} from './SearchAndFilterBar';

export interface DashboardProps {
    projectKey: string;
    onOpenRoom: (meetingId: string) => void;
}

export function Dashboard({ projectKey, onOpenRoom }: DashboardProps) {
    const [filters, setFilters] = useState<MeetingFilterValue>({
        sort: DEFAULT_PROJECT_MEETING_SORT,
    });
    const [selectedMeeting, setSelectedMeeting] = useState<{
        id: string;
        focus: 'details' | 'history';
    } | null>(null);
    const [editingMeetingId, setEditingMeetingId] = useState<string | null>(
        null,
    );
    const [isScheduleOpen, setScheduleOpen] = useState(false);
    const [isStartOpen, setStartOpen] = useState(false);
    const [feedback, setFeedback] = useState<{
        appearance: 'success' | 'error';
        message: string;
    } | null>(null);
    const showSuccess = (message: string) =>
        setFeedback({ appearance: 'success', message });

    const permissions = useMeetingPermissions(projectKey);
    const projectMeetings = useProjectMeetings(
        { projectKey, ...filters },
        permissions.canViewMeeting && !permissions.isLoading,
    );
    const startMeeting = useStartMeeting();
    const confirmAction = useConfirmMeetingAction(setFeedback, 'inline');
    const hostConflictGuard = useHostConflictGuard('inline');

    const handleAction = (action: MeetingAction, meeting: Meeting) => {
        switch (action) {
            case 'EDIT':
                setEditingMeetingId(meeting.id);
                break;
            case 'CANCEL':
                confirmAction.request('CANCEL', meeting);
                break;
            case 'START':
                hostConflictGuard.guard(meeting.issueKey, () => {
                    startMeeting.mutate(meeting.id, {
                        onSuccess: (_result, meetingId) =>
                            onOpenRoom(meetingId),
                    });
                });
                break;
            case 'JOIN':
                onOpenRoom(meeting.id);
                break;
            case 'END':
                confirmAction.request('END', meeting);
                break;
            case 'DELETE':
                confirmAction.request('DELETE', meeting);
                break;
            case 'VIEW_HISTORY':
                setSelectedMeeting({ id: meeting.id, focus: 'history' });
                break;
            case 'VIEW_DETAIL':
                setSelectedMeeting({ id: meeting.id, focus: 'details' });
                break;
        }
    };

    if (permissions.isLoading) {
        return (
            <div className='mx-auto max-w-7xl px-6 py-10'>
                <LoadingState label='Checking meeting permissions…' />
            </div>
        );
    }
    if (permissions.error)
        return (
            <div className='mx-auto max-w-7xl p-6'>
                <ErrorState message={permissions.error.message} />
            </div>
        );
    if (!permissions.canViewMeeting) return <NoPermissionState />;

    return (
        <div className='w-full bg-[var(--surface)]'>
            <DashboardHeader
                canEditMeeting={permissions.canEditMeeting}
                onStartMeeting={() =>
                    hostConflictGuard.guard(undefined, () => setStartOpen(true))
                }
                onScheduleMeeting={() => setScheduleOpen(true)}
            />
            <div className='border-b bg-[var(--surface)] px-4 py-2.5 sm:px-6'>
                <SearchAndFilterBar
                    projectKey={projectKey}
                    value={filters}
                    onChange={setFilters}
                />
            </div>
            {feedback && (
                <div className='px-4 sm:px-6'>
                    <InlineFeedback
                        appearance={feedback.appearance}
                        message={feedback.message}
                        onDismiss={() => setFeedback(null)}
                    />
                </div>
            )}
            <MeetingListTable
                meetings={projectMeetings.meetings}
                permissions={permissions}
                isLoading={projectMeetings.loading}
                error={projectMeetings.error}
                pageNumber={projectMeetings.pageNumber}
                hasPreviousPage={projectMeetings.hasPreviousPage}
                hasNextPage={projectMeetings.hasNextPage}
                onPreviousPage={projectMeetings.previousPage}
                onNextPage={projectMeetings.nextPage}
                onBatchDeleteSuccess={projectMeetings.resetPagination}
                onSelect={(meeting) =>
                    setSelectedMeeting({ id: meeting.id, focus: 'details' })
                }
                onAction={handleAction}
            />
            {selectedMeeting && (
                <MeetingDetailPanel
                    meetingId={selectedMeeting.id}
                    focus={selectedMeeting.focus}
                    onClose={() => setSelectedMeeting(null)}
                />
            )}
            <ScheduleMeetingModal
                isOpen={isScheduleOpen}
                projectKey={projectKey}
                onClose={() => setScheduleOpen(false)}
                onSubmitted={() => showSuccess('Meeting scheduled.')}
            />
            <StartInstantMeetingModal
                isOpen={isStartOpen}
                projectKey={projectKey}
                onClose={() => setStartOpen(false)}
                onStarted={onOpenRoom}
            />
            {editingMeetingId && (
                <EditMeetingModal
                    isOpen
                    meetingId={editingMeetingId}
                    onClose={() => setEditingMeetingId(null)}
                    onSaved={() => {
                        setEditingMeetingId(null);
                        showSuccess('Meeting updated.');
                    }}
                />
            )}
            {hostConflictGuard.conflictingMeeting && (
                <ActiveMeetingWarningDialog
                    conflictingMeeting={hostConflictGuard.conflictingMeeting}
                    onClose={hostConflictGuard.dismiss}
                    onConfirm={hostConflictGuard.confirm}
                />
            )}
            {confirmAction.pending && (
                <ConfirmMeetingActionDialog
                    action={confirmAction.pending.action}
                    meeting={confirmAction.pending.meeting}
                    isLoading={confirmAction.isLoading}
                    error={confirmAction.error}
                    onConfirm={confirmAction.confirm}
                    onClose={confirmAction.dismiss}
                />
            )}
        </div>
    );
}
