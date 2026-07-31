/**
 * IssueMeetingsPanel — the Issue Panel's single meeting surface: create
 * actions, search/filter, and one unified list covering every meeting status
 * for this issue (replaces the old split Current/Upcoming/History sections).
 *
 * Data comes from `useIssueMeetings` → the `getIssueMeetings` resolver, backed
 * by Forge KVS (`app/src/meetingStore.ts`). There is no mock db anymore —
 * `mocks/db.ts` was deleted with the KVS migration. In Forge, Join/Start open a
 * large platform modal so the meeting room is never constrained by this narrow
 * panel; standalone development falls back to the Project Page.
 */
import { useState } from 'react';
import {
    EmptyState,
    ErrorState,
    InlineFeedback,
    LoadingState,
    MeetingActionMenu,
    MeetingCard,
    MeetingDetailDialog,
    ScheduleMeetingModal,
    StartInstantMeetingModal,
} from '../../components/shared';
import { Button, Icon } from '../../components/ui';
import type { CurrentIssueContextValue, MeetingAction } from '../../domain';
import { useIssueMeetings } from '../../hooks/useIssueMeetings';
import { useIssuePanelInstantModal } from '../../hooks/useIssuePanelInstantModal';
import { useIssuePanelMeetingDetailModal } from '../../hooks/useIssuePanelMeetingDetailModal';
import { useIssuePanelMeetingRoomModal } from '../../hooks/useIssuePanelMeetingRoomModal';
import { useIssuePanelScheduleModal } from '../../hooks/useIssuePanelScheduleModal';
import {
    useCancelMeeting,
    useEndMeeting,
    useStartMeeting,
} from '../../hooks/useMeetingMutations';
import { useMeetingParticipants } from '../../hooks/useMeetingParticipants';
import { useMeetingPermissions } from '../../hooks/useMeetingPermission';
import { IssueMeetingsFilterBar } from './IssueMeetingsFilterBar';
import {
    filterAndSortIssueMeetings,
    type IssueMeetingsFilterValue,
} from './issueMeetingsFilter';
import { StartInstantMeetingButton } from './StartInstantMeetingButton';

export interface IssueMeetingsPanelProps {
    issue: CurrentIssueContextValue;
    /** DEV-only: standalone `pnpm ui:dev` has no real Forge modules to
     * navigate between, so this drives the same surface flip a real
     * Issue-Panel-to-Project-Page navigation would otherwise cause. */
    onDevNavigateToProjectPage?: () => void;
}

export function IssueMeetingsPanel({
    issue,
    onDevNavigateToProjectPage,
}: IssueMeetingsPanelProps) {
    const { meetings, loading, error } = useIssueMeetings(issue.issueKey);
    const permissions = useMeetingPermissions(issue.projectKey);
    const openMeetingRoom = useIssuePanelMeetingRoomModal(
        onDevNavigateToProjectPage,
    );

    const [filter, setFilter] = useState<IssueMeetingsFilterValue>({});
    const [feedback, setFeedback] = useState<string | null>(null);

    const scheduleModal = useIssuePanelScheduleModal(() =>
        setFeedback('Meeting scheduled.'),
    );
    const instantModal = useIssuePanelInstantModal((meetingId) =>
        openMeetingRoom(issue.projectKey, meetingId),
    );
    const detailModal = useIssuePanelMeetingDetailModal();
    const cancelMeeting = useCancelMeeting();
    const startMeeting = useStartMeeting();
    const endMeeting = useEndMeeting();
    const { participants, loading: participantsLoading } =
        useMeetingParticipants(
            detailModal.devMeeting?.id,
            detailModal.devMeeting?.projectKey,
        );

    const visibleMeetings = filterAndSortIssueMeetings(meetings, filter);

    const handleAction = (action: MeetingAction, meetingId: string) => {
        const meeting = meetings.find((m) => m.id === meetingId);
        if (!meeting) return;
        switch (action) {
            case 'EDIT':
                scheduleModal.open({
                    issueKey: issue.issueKey,
                    projectKey: issue.projectKey,
                    meeting,
                });
                break;
            case 'CANCEL':
                cancelMeeting.mutate(meeting.id, {
                    onSuccess: () => setFeedback('Meeting canceled.'),
                });
                break;
            case 'START':
                startMeeting.mutate(meeting.id, {
                    onSuccess: (_result, meetingId) =>
                        openMeetingRoom(issue.projectKey, meetingId),
                });
                break;
            case 'JOIN':
                openMeetingRoom(issue.projectKey, meeting.id);
                break;
            case 'END':
                endMeeting.mutate(meeting.id, {
                    onSuccess: () => setFeedback('Meeting ended.'),
                });
                break;
            case 'VIEW_DETAIL':
            case 'VIEW_HISTORY':
                detailModal.open(meeting);
                break;
        }
    };

    return (
        <section aria-label='Meetings'>
            <div className='mb-3 grid grid-cols-2 gap-2'>
                <StartInstantMeetingButton
                    className='w-full'
                    issueKey={issue.issueKey}
                    projectKey={issue.projectKey}
                    onOpenInstantModal={instantModal.open}
                />
                <Button
                    size='sm'
                    className='w-full'
                    onClick={() =>
                        scheduleModal.open({
                            issueKey: issue.issueKey,
                            projectKey: issue.projectKey,
                        })
                    }
                    leadingIcon={<Icon name='calendar' size={15} />}
                >
                    Schedule meeting
                </Button>
            </div>

            <IssueMeetingsFilterBar value={filter} onChange={setFilter} />

            <div className='mt-3'>
                {loading && <LoadingState label='Loading meetings…' />}
                {error && <ErrorState message={error.message} />}
                {!loading && !error && visibleMeetings.length === 0 && (
                    <EmptyState
                        header={
                            meetings.length === 0
                                ? 'No meetings yet'
                                : 'No matching meetings'
                        }
                        description={
                            meetings.length === 0
                                ? 'Start an instant meeting or schedule one for later.'
                                : 'Try a different search term or status filter.'
                        }
                    />
                )}
                {!loading && !error && visibleMeetings.length > 0 && (
                    <div className='space-y-2.5'>
                        {visibleMeetings.map((meeting) => (
                            <MeetingCard
                                key={meeting.id}
                                meeting={meeting}
                                density='compact'
                                actions={
                                    <MeetingActionMenu
                                        meetingId={meeting.id}
                                        meeting={meeting}
                                        permissions={permissions}
                                        onAction={handleAction}
                                    />
                                }
                            />
                        ))}
                    </div>
                )}
            </div>

            {feedback && (
                <InlineFeedback
                    appearance='success'
                    message={feedback}
                    onDismiss={() => setFeedback(null)}
                />
            )}

            {import.meta.env.DEV && (
                <ScheduleMeetingModal
                    isOpen={scheduleModal.isDevOpen}
                    issueKey={issue.issueKey}
                    projectKey={issue.projectKey}
                    meeting={scheduleModal.devPayload?.meeting}
                    onClose={scheduleModal.closeDev}
                    onSubmitted={() => {
                        setFeedback(
                            scheduleModal.devPayload?.meeting
                                ? 'Meeting updated.'
                                : 'Meeting scheduled.',
                        );
                        scheduleModal.closeDev();
                    }}
                />
            )}

            {import.meta.env.DEV && instantModal.isDevOpen && (
                <StartInstantMeetingModal
                    isOpen
                    issueKey={
                        instantModal.devPayload?.issueKey ?? issue.issueKey
                    }
                    projectKey={
                        instantModal.devPayload?.projectKey ?? issue.projectKey
                    }
                    onClose={instantModal.closeDev}
                    onStarted={(meetingId) => {
                        instantModal.closeDev();
                        openMeetingRoom(issue.projectKey, meetingId);
                    }}
                />
            )}

            {import.meta.env.DEV && detailModal.devMeeting && (
                <MeetingDetailDialog
                    meeting={detailModal.devMeeting}
                    participants={participants}
                    isLoading={participantsLoading}
                    onClose={detailModal.closeDev}
                />
            )}
        </section>
    );
}
