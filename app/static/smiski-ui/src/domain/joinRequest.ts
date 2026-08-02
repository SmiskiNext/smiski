/** A participant waiting for the meeting host to decide their join request. */
export interface PendingJoinRequest {
    requestId: string;
    accountId: string;
    displayName: string;
    status: 'PENDING';
    requestedAt: string;
    expiresAt: string;
}

/** Offset-based page returned by the host-only pending join-request endpoint. */
export interface PendingJoinRequestsPage {
    requests: PendingJoinRequest[];
    total: number;
    offset: number;
    pageSize: number;
}

export interface PendingJoinRequestsPageParams {
    offset?: number;
    pageSize?: number;
}

/** Per-request result returned by a batch accept or decline operation. */
export interface JoinRequestDecision {
    requestId: string;
    status: 'APPROVED' | 'DENIED' | 'FAILED';
    token: string | null;
    roomName: string | null;
    reason: string | null;
}
