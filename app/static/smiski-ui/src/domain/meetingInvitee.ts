/** RSVP states returned by the meet service for an active invitee. */
export type MeetingInviteeStatus =
    | 'NEEDS_ACTION'
    | 'ACCEPTED'
    | 'DECLINED'
    | 'TENTATIVE';

/** An active invitee embedded in a meeting-detail response. */
export interface MeetingInvitee {
    /** Invitee identity used by the batch-delete endpoint. */
    id: string;
    accountId: string;
    email: string;
    displayName: string;
    status: MeetingInviteeStatus;
    invitedAt?: string;
    respondedAt?: string;
}
