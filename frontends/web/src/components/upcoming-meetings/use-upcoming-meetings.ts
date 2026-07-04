'use client';

import { useCallback, useEffect, useState } from 'react';
import {
    cancelMeeting,
    endMeeting,
    listHostMeetings,
} from '@/generated/sdk.gen.ts';
import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';
import type {
    ConfirmCancelMessages,
    ConfirmEndMessages,
    DetailSheetTab,
    UpcomingMeetingsState,
    UseUpcomingMeetingsResult,
} from './types.ts';

const INVALID_STATUS_TRANSITION_CODE = 'INVALID_STATUS_TRANSITION';
const DEFAULT_SHEET_TAB: DetailSheetTab = 'overview';

function isUpcoming(meeting: MeetingManagementMeetingResponse): boolean {
    return meeting.status === 'LIVE' || meeting.status === 'SCHEDULED';
}

function compareUpcomingMeetings(
    a: MeetingManagementMeetingResponse,
    b: MeetingManagementMeetingResponse,
): number {
    const aIsLive = a.status === 'LIVE';
    const bIsLive = b.status === 'LIVE';
    if (aIsLive !== bIsLive) return aIsLive ? -1 : 1;

    const aHasStart = Boolean(a.startTime);
    const bHasStart = Boolean(b.startTime);
    if (aHasStart !== bHasStart) return aHasStart ? -1 : 1;
    if (!aHasStart) return 0;

    return (
        new Date(a.startTime as string).getTime()
        - new Date(b.startTime as string).getTime()
    );
}

/**
 * Fetches host meetings, filters to live and scheduled meetings sorted with
 * live meetings first then scheduled by ascending start time, and exposes
 * centralised action handlers for meeting selection, copy-link feedback,
 * and cancellation. The detail sheet manages its own tab state but accepts
 * an initial tab from this hook so the row's overflow menu can deep-link.
 */
export function useUpcomingMeetings(): UseUpcomingMeetingsResult {
    const [listState, setListState] = useState<UpcomingMeetingsState>({
        phase: 'LOADING',
    });
    const [selectedMeeting, setSelectedMeeting] =
        useState<MeetingManagementMeetingResponse | null>(null);
    const [selectedSheetTab, setSelectedSheetTab] =
        useState<DetailSheetTab>(DEFAULT_SHEET_TAB);
    const [cancelTarget, setCancelTarget] =
        useState<MeetingManagementMeetingResponse | null>(null);
    const [isCancelling, setIsCancelling] = useState(false);
    const [cancelError, setCancelError] = useState<string | null>(null);
    const [cancelFeedback, setCancelFeedback] = useState(false);
    const [endTarget, setEndTarget] =
        useState<MeetingManagementMeetingResponse | null>(null);
    const [isEnding, setIsEnding] = useState(false);
    const [endError, setEndError] = useState<string | null>(null);
    const [endFeedback, setEndFeedback] = useState(false);
    const [copiedShortCode, setCopiedShortCode] = useState<string | null>(null);

    const loadMeetings = useCallback(() => {
        setListState({ phase: 'LOADING' });

        listHostMeetings()
            .then(({ data }) => {
                const all = data?.content ?? [];
                const upcoming = all
                    .filter(isUpcoming)
                    .sort(compareUpcomingMeetings);

                if (upcoming.length === 0) {
                    setListState({ phase: 'EMPTY' });
                } else {
                    setListState({ phase: 'SUCCESS', meetings: upcoming });
                }
            })
            .catch(() => {
                setListState({ phase: 'ERROR' });
            });
    }, []);

    useEffect(() => {
        loadMeetings();
    }, [loadMeetings]);

    const retry = useCallback(() => {
        loadMeetings();
    }, [loadMeetings]);

    const selectMeeting = useCallback(
        (
            meeting: MeetingManagementMeetingResponse,
            tab: DetailSheetTab = DEFAULT_SHEET_TAB,
        ) => {
            setSelectedMeeting(meeting);
            setSelectedSheetTab(tab);
        },
        [],
    );

    const clearSelectedMeeting = useCallback(() => {
        setSelectedMeeting(null);
        setSelectedSheetTab(DEFAULT_SHEET_TAB);
    }, []);

    const requestCancel = useCallback(
        (meeting: MeetingManagementMeetingResponse) => {
            setCancelError(null);
            setCancelTarget(meeting);
        },
        [],
    );

    const dismissCancel = useCallback(() => {
        setCancelTarget(null);
        setCancelError(null);
    }, []);

    const confirmCancel = useCallback(
        async (messages: ConfirmCancelMessages) => {
            if (!cancelTarget?.id) return;

            setIsCancelling(true);
            setCancelError(null);

            try {
                await cancelMeeting({
                    path: { id: cancelTarget.id },
                    throwOnError: true,
                });

                const cancelledId = cancelTarget.id;
                setCancelTarget(null);
                setSelectedMeeting(null);
                setSelectedSheetTab(DEFAULT_SHEET_TAB);
                setCancelFeedback(true);
                setTimeout(() => setCancelFeedback(false), 3000);
                setListState((prev) => {
                    if (prev.phase !== 'SUCCESS') return prev;
                    const remaining = prev.meetings.filter(
                        (m) => m.id !== cancelledId,
                    );
                    return remaining.length === 0
                        ? { phase: 'EMPTY' }
                        : { phase: 'SUCCESS', meetings: remaining };
                });
            } catch (error) {
                if (
                    error instanceof ApiFailError
                    && error.code === INVALID_STATUS_TRANSITION_CODE
                ) {
                    setCancelError(messages.statusConflict);
                    loadMeetings();
                } else if (
                    error instanceof ApiFailError
                    || error instanceof ApiError
                ) {
                    setCancelError(error.message);
                } else {
                    setCancelError(messages.errorFallback);
                }
            } finally {
                setIsCancelling(false);
            }
        },
        [cancelTarget, loadMeetings],
    );

    const requestEnd = useCallback(
        (meeting: MeetingManagementMeetingResponse) => {
            setEndError(null);
            setEndTarget(meeting);
        },
        [],
    );

    const dismissEnd = useCallback(() => {
        setEndTarget(null);
        setEndError(null);
    }, []);

    const confirmEnd = useCallback(
        async (messages: ConfirmEndMessages) => {
            if (!endTarget?.id) return;

            setIsEnding(true);
            setEndError(null);

            try {
                await endMeeting({
                    path: { id: endTarget.id },
                    throwOnError: true,
                });

                const endedId = endTarget.id;
                setEndTarget(null);
                setSelectedMeeting(null);
                setSelectedSheetTab(DEFAULT_SHEET_TAB);
                setEndFeedback(true);
                setTimeout(() => setEndFeedback(false), 3000);
                setListState((prev) => {
                    if (prev.phase !== 'SUCCESS') return prev;
                    const remaining = prev.meetings.filter(
                        (m) => m.id !== endedId,
                    );
                    return remaining.length === 0
                        ? { phase: 'EMPTY' }
                        : { phase: 'SUCCESS', meetings: remaining };
                });
            } catch (error) {
                if (
                    error instanceof ApiFailError
                    || error instanceof ApiError
                ) {
                    setEndError(error.message);
                } else {
                    setEndError(messages.errorFallback);
                }
            } finally {
                setIsEnding(false);
            }
        },
        [endTarget],
    );

    const copyShortCode = useCallback(async (shortCode: string) => {
        try {
            await navigator.clipboard.writeText(shortCode);
            setCopiedShortCode(shortCode);
            setTimeout(() => setCopiedShortCode(null), 2000);
        } catch {
            return;
        }
    }, []);

    return {
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
        actions: {
            selectMeeting,
            clearSelectedMeeting,
            requestCancel,
            dismissCancel,
            confirmCancel,
            requestEnd,
            dismissEnd,
            confirmEnd,
            copyShortCode,
            retry,
        },
    };
}
