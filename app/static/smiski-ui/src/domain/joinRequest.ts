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

function preferPopulatedValue(next: string, current: string): string {
    return next || current;
}

/**
 * Applies a realtime join request to the first cached page.
 *
 * REST may provide fields that are not present in older SSE payloads, so an
 * existing value is retained when the event contains an empty placeholder.
 */
export function upsertPendingJoinRequestPage(
    page: PendingJoinRequestsPage,
    request: PendingJoinRequest,
): PendingJoinRequestsPage {
    if (page.offset > 0) return page;

    const existingIndex = page.requests.findIndex(
        (candidate) => candidate.requestId === request.requestId,
    );
    if (existingIndex < 0) {
        return {
            ...page,
            requests: [request, ...page.requests].slice(0, page.pageSize),
            total: page.total + 1,
        };
    }

    const existing = page.requests[existingIndex];
    const merged = {
        ...existing,
        ...request,
        requestedAt: preferPopulatedValue(
            request.requestedAt,
            existing.requestedAt,
        ),
        expiresAt: preferPopulatedValue(request.expiresAt, existing.expiresAt),
    };
    const requests = [...page.requests];
    requests[existingIndex] = merged;
    return { ...page, requests };
}

/** Removes terminal decisions from a cached page without waiting for REST. */
export function removePendingJoinRequestsFromPage(
    page: PendingJoinRequestsPage,
    requestIds: ReadonlySet<string>,
): PendingJoinRequestsPage {
    if (requestIds.size === 0) return page;

    return {
        ...page,
        requests: page.requests.filter(
            (request) => !requestIds.has(request.requestId),
        ),
        total: Math.max(0, page.total - requestIds.size),
    };
}
