/**
 * A Jira user assignable within the current project — the pool offered when
 * picking meeting participants. Intentionally the same thin slice as
 * `JiraIssue`: just what the picker needs, not the full Jira user model.
 */
export interface ProjectMember {
    /** Jira accountId — stable identity used everywhere else (Meeting.hostId, Participant.accountId, ...). */
    accountId: string;
    displayName: string;
    email?: string;
    avatarUrl?: string;
    /** IANA time-zone id from the Jira profile, e.g. `Asia/Ho_Chi_Minh`. */
    timeZone?: string;
}
