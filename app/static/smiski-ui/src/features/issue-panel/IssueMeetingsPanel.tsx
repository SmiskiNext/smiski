/**
 * IssueMeetingsPanel — the Issue Panel's single meeting surface: create
 * actions, search/filter, and one unified list covering every meeting status
 * for this issue (replaces the old split Current/Upcoming/History sections).
 *
 * Data comes from `useIssueMeetings` → the real `meet` backend's `list`
 * operation (SDK over Forge Remote, see `api/meetings.ts`'s
 * `listIssueMeetings`). There is no mock db — meeting persistence is never
 * mocked. Join/Start navigate to the Project Page's meeting room
 * (`useNavigateToMeetingRoom`) — Issue Panel and Project Page are separate
 * Forge modules/iframes, so this narrow panel never has room to render the
 * meeting itself.
 *
 * Every dialog this surface triggers opens as a Forge platform modal over the
 * whole product window (`hooks/useIssuePanel*Modal.ts`), so the panel itself
 * renders no dialogs inline.
 */
import { useState } from 'react';
import {
    EmptyState,
    ErrorState,
    InlineFeedback,
    LoadingState,
    MeetingActionMenu,
    MeetingCard,
    NoPermissionState,
} from '../../components/shared';
import { Button, Icon } from '../../components/ui';
import type { CurrentIssueContextValue, MeetingAction } from '../../domain';
import { useConfirmMeetingAction } from '../../hooks/useConfirmMeetingAction';
import { useHostConflictGuard } from '../../hooks/useHostConflictGuard';
import { useIssueMeetings } from '../../hooks/useIssueMeetings';
import { useIssuePanelInstantModal } from '../../hooks/useIssuePanelInstantModal';
import { useIssuePanelMeetingDetailModal } from '../../hooks/useIssuePanelMeetingDetailModal';
import { useIssuePanelScheduleModal } from '../../hooks/useIssuePanelScheduleModal';
import { useIssuePanelSettingsModal } from '../../hooks/useIssuePanelSettingsModal';
import { useStartMeeting } from '../../hooks/useMeetingMutations';
import { useMeetingPermissions } from '../../hooks/useMeetingPermission';
import { useNavigateToMeetingRoom } from '../../hooks/useNavigateToMeetingRoom';
import { IssueMeetingsFilterBar } from './IssueMeetingsFilterBar';
import {
    filterAndSortIssueMeetings,
    type IssueMeetingsFilterValue,
} from './issueMeetingsFilter';
import { StartInstantMeetingButton } from './StartInstantMeetingButton';

export interface IssueMeetingsPanelProps {
    issue: CurrentIssueContextValue;
}

export function IssueMeetingsPanel({ issue }: IssueMeetingsPanelProps) {
    const permissions = useMeetingPermissions(issue.projectKey);
    const { meetings, loading, error } = useIssueMeetings(
        issue.issueKey,
        issue.projectKey,
        permissions.canViewMeeting && !permissions.isLoading,
    );
    const openMeetingRoom = useNavigateToMeetingRoom();

    const [filter, setFilter] = useState<IssueMeetingsFilterValue>({});
    const [feedback, setFeedback] = useState<{
        appearance: 'success' | 'error';
        message: string;
    } | null>(null);
    const showSuccess = (message: string) =>
        setFeedback({ appearance: 'success', message });

    const scheduleModal = useIssuePanelScheduleModal(() =>
        showSuccess('Meeting scheduled.'),
    );
    const instantModal = useIssuePanelInstantModal((meetingId) =>
        openMeetingRoom(issue.projectKey, meetingId),
    );
    const detailModal = useIssuePanelMeetingDetailModal();
    const settingsModal = useIssuePanelSettingsModal();
    const hostConflictGuard = useHostConflictGuard('platform-modal');
    const confirmAction = useConfirmMeetingAction(
        setFeedback,
        'platform-modal',
    );
    const startMeeting = useStartMeeting();

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
                confirmAction.request('CANCEL', meeting);
                break;
            case 'START':
                hostConflictGuard.guard(meeting.issueKey, () => {
                    startMeeting.mutate(meeting.id, {
                        onSuccess: (_result, meetingId) =>
                            openMeetingRoom(issue.projectKey, meetingId),
                    });
                });
                break;
            case 'JOIN':
                openMeetingRoom(issue.projectKey, meeting.id);
                break;
            case 'END':
                confirmAction.request('END', meeting);
                break;
            case 'SETTINGS':
                settingsModal.open(meeting.id);
                break;
            case 'VIEW_DETAIL':
            case 'VIEW_HISTORY':
                detailModal.open(meeting);
                break;
        }
    };

    if (permissions.isLoading) {
        return (
            <section aria-label='Meetings'>
                <LoadingState label='Checking meeting permissions…' />
            </section>
        );
    }
    if (permissions.error) {
        return (
            <section aria-label='Meetings'>
                <ErrorState message={permissions.error.message} />
            </section>
        );
    }
    if (!permissions.canViewMeeting) {
        return (
            <section aria-label='Meetings'>
                <NoPermissionState />
            </section>
        );
    }

    return (
        <section aria-label='Meetings'>
            {permissions.canEditMeeting && (
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
            )}

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
                                ? permissions.canEditMeeting
                                    ? 'Start an instant meeting or schedule one for later.'
                                    : 'No meetings have been scheduled for this issue yet.'
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
                    appearance={feedback.appearance}
                    message={feedback.message}
                    onDismiss={() => setFeedback(null)}
                />
            )}
        </section>
    );
}
