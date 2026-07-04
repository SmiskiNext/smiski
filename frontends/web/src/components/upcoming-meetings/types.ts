import type { MeetingManagementMeetingResponse } from '@/generated/types.gen.ts';

/** Discriminated-union state for the upcoming meetings list. */
export type UpcomingMeetingsState =
    | { phase: 'LOADING' }
    | { phase: 'SUCCESS'; meetings: MeetingManagementMeetingResponse[] }
    | { phase: 'EMPTY' }
    | { phase: 'ERROR' };

/** Localised messages required to confirm a meeting cancellation. */
export type ConfirmCancelMessages = {
    errorFallback: string;
    statusConflict: string;
};

export type ConfirmEndMessages = {
    errorFallback: string;
};

/** Tab keys exposed by the upcoming meeting detail sheet. */
export type DetailSheetTab = 'overview' | 'invitees' | 'settings';

/** Actions exposed by the upcoming meetings hook to child components. */
export type UpcomingMeetingActions = {
    selectMeeting: (
        meeting: MeetingManagementMeetingResponse,
        tab?: DetailSheetTab,
    ) => void;
    clearSelectedMeeting: () => void;
    requestCancel: (meeting: MeetingManagementMeetingResponse) => void;
    dismissCancel: () => void;
    confirmCancel: (messages: ConfirmCancelMessages) => Promise<void>;
    requestEnd: (meeting: MeetingManagementMeetingResponse) => void;
    dismissEnd: () => void;
    confirmEnd: (messages: ConfirmEndMessages) => Promise<void>;
    copyShortCode: (shortCode: string) => Promise<void>;
    retry: () => void;
};

/** Full state bag returned from the upcoming meetings hook. */
export type UseUpcomingMeetingsResult = {
    listState: UpcomingMeetingsState;
    selectedMeeting: MeetingManagementMeetingResponse | null;
    selectedSheetTab: DetailSheetTab;
    cancelTarget: MeetingManagementMeetingResponse | null;
    isCancelling: boolean;
    cancelError: string | null;
    cancelFeedback: boolean;
    endTarget: MeetingManagementMeetingResponse | null;
    isEnding: boolean;
    endError: string | null;
    endFeedback: boolean;
    copiedShortCode: string | null;
    actions: UpcomingMeetingActions;
};
