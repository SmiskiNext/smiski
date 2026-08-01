import { useState } from 'react';
import {
    ActiveMeetingWarningDialog,
    ErrorState,
    InlineFeedback,
    LoadingState,
    NoPermissionState,
    ScheduleMeetingModal,
    StartInstantMeetingModal,
} from '../../../components/shared';
import type { Meeting, MeetingAction } from '../../../domain';
import { useHostConflictGuard } from '../../../hooks/useHostConflictGuard';
import {
    useCancelMeeting,
    useEndMeeting,
    useStartMeeting,
} from '../../../hooks/useMeetingMutations';
import { useMeetingPermissions } from '../../../hooks/useMeetingPermission';
import { useProjectMeetings } from '../../../hooks/useProjectMeetings';
import { DashboardHeader } from './DashboardHeader';
import { MeetingDetailPanel } from './MeetingDetailPanel';
import { MeetingListTable } from './MeetingListTable';
import {
    type MeetingFilterValue,
    SearchAndFilterBar,
} from './SearchAndFilterBar';

export interface DashboardProps {
    projectKey: string;
    onOpenRoom: (meetingId: string) => void;
}

export function Dashboard({ projectKey, onOpenRoom }: DashboardProps) {
    const [filters, setFilters] = useState<MeetingFilterValue>({});
    const [selectedMeeting, setSelectedMeeting] = useState<{
        id: string;
        focus: 'details' | 'history';
    } | null>(null);
    const [editingMeeting, setEditingMeeting] = useState<Meeting | null>(null);
    const [isScheduleOpen, setScheduleOpen] = useState(false);
    const [isStartOpen, setStartOpen] = useState(false);
    const [feedback, setFeedback] = useState<string | null>(null);

    const permissions = useMeetingPermissions(projectKey);
    const { meetings, loading, error } = useProjectMeetings(
        { projectKey, ...filters },
        permissions.canViewMeeting && !permissions.isLoading,
    );
    const cancelMeeting = useCancelMeeting();
    const startMeeting = useStartMeeting();
    const endMeeting = useEndMeeting();
    const hostConflictGuard = useHostConflictGuard('inline');

    const handleAction = (action: MeetingAction, meeting: Meeting) => {
        switch (action) {
            case 'EDIT':
                setEditingMeeting(meeting);
                break;
            case 'CANCEL':
                cancelMeeting.mutate(meeting.id, {
                    onSuccess: () => setFeedback('Meeting canceled.'),
                });
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
                endMeeting.mutate(meeting.id, {
                    onSuccess: () => setFeedback('Meeting ended.'),
                });
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
                    hostConflictGuard.guard(undefined, () =>
                        setStartOpen(true),
                    )
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
                        appearance='success'
                        message={feedback}
                        onDismiss={() => setFeedback(null)}
                    />
                </div>
            )}
            <MeetingListTable
                meetings={meetings}
                permissions={permissions}
                isLoading={loading}
                error={error}
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
                onSubmitted={() => setFeedback('Meeting scheduled.')}
            />
            <StartInstantMeetingModal
                isOpen={isStartOpen}
                projectKey={projectKey}
                onClose={() => setStartOpen(false)}
                onStarted={onOpenRoom}
            />
            {editingMeeting && (
                <ScheduleMeetingModal
                    isOpen
                    meeting={editingMeeting}
                    issueKey={editingMeeting.issueKey}
                    onClose={() => setEditingMeeting(null)}
                    onSubmitted={() => setFeedback('Meeting updated.')}
                />
            )}
            {hostConflictGuard.conflictingMeeting && (
                <ActiveMeetingWarningDialog
                    conflictingMeeting={hostConflictGuard.conflictingMeeting}
                    onClose={hostConflictGuard.dismiss}
                    onConfirm={hostConflictGuard.confirm}
                />
            )}
        </div>
    );
}
