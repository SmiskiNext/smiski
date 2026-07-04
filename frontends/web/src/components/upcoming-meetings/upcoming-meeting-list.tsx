'use client';

import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button.tsx';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { CancelMeetingDialog } from './cancel-meeting-dialog.tsx';
import { EndMeetingDialog } from './end-meeting-dialog.tsx';
import { MeetingDetailSheet } from './meeting-detail-sheet.tsx';
import { UpcomingMeetingCard } from './upcoming-meeting-card.tsx';
import { useUpcomingMeetings } from './use-upcoming-meetings.ts';

/**
 * Renders the upcoming host meetings list with loading, empty, error, and
 * success states, and wires all meeting-level dialogs through the shared hook.
 */
export function UpcomingMeetingList() {
    const t = useTranslations('workspace.home');
    const {
        listState,
        selectedMeeting,
        selectedSheetTab,
        cancelTarget,
        isCancelling,
        cancelError,
        cancelFeedback,
        endTarget,
        isEnding,
        endError,
        endFeedback,
        copiedShortCode,
        actions,
    } = useUpcomingMeetings();

    function handleOpenSettings(meetingId: string) {
        if (listState.phase !== 'SUCCESS') return;
        const meeting = listState.meetings.find(
            (item) => item.id === meetingId,
        );
        if (meeting) {
            actions.selectMeeting(meeting, 'settings');
        }
    }

    return (
        <>
            <div className='mt-8 space-y-3'>
                {listState.phase === 'LOADING' && (
                    <p className='text-lg text-text-muted'>
                        {t('meetingsLoading')}
                    </p>
                )}

                {listState.phase === 'EMPTY' && (
                    <p className='text-lg text-text-muted'>
                        {t('meetingsEmpty')}
                    </p>
                )}

                {listState.phase === 'ERROR' && (
                    <div className='space-y-2'>
                        <p className='text-lg text-error'>
                            {t('meetingsError')}
                        </p>
                        <Button
                            onClick={actions.retry}
                            type='button'
                            variant='outline'
                        >
                            {t('retryLoadMeetings')}
                        </Button>
                    </div>
                )}

                {cancelFeedback && (
                    <p className='text-sm text-text-muted'>
                        {t('cancelSuccess')}
                    </p>
                )}

                {endFeedback && (
                    <p className='text-sm text-text-muted'>{t('endSuccess')}</p>
                )}

                {listState.phase === 'SUCCESS'
                    && listState.meetings.map(
                        (meeting: MeetingManagementMeetingResponse) => (
                            <UpcomingMeetingCard
                                copiedShortCode={copiedShortCode}
                                key={meeting.id}
                                meeting={meeting}
                                onCancel={actions.requestCancel}
                                onEnd={actions.requestEnd}
                                onCardClick={actions.selectMeeting}
                                onCopyLink={actions.copyShortCode}
                                onOpenSettings={handleOpenSettings}
                            />
                        ),
                    )}
            </div>

            <MeetingDetailSheet
                copiedShortCode={copiedShortCode}
                initialTab={selectedSheetTab}
                meeting={selectedMeeting}
                onCancel={actions.requestCancel}
                onEnd={actions.requestEnd}
                onClose={actions.clearSelectedMeeting}
                onCopyLink={actions.copyShortCode}
                open={Boolean(selectedMeeting)}
            />

            <EndMeetingDialog
                endError={endError}
                isEnding={isEnding}
                meeting={endTarget}
                onClose={actions.dismissEnd}
                onConfirm={actions.confirmEnd}
                open={Boolean(endTarget)}
            />

            <CancelMeetingDialog
                cancelError={cancelError}
                isCancelling={isCancelling}
                meeting={cancelTarget}
                onClose={actions.dismissCancel}
                onConfirm={actions.confirmCancel}
                open={Boolean(cancelTarget)}
            />
        </>
    );
}
