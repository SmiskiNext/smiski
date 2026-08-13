import type { PendingJoinRequest } from '../domain';
import { apiConfig } from './config';
import { consumeSse, type SseMessage } from './sseClient';

const RECONNECT_DELAY_MS = 1_000;
const JOIN_DECISION_TIMEOUT_MS = 10 * 60 * 1_000;

export interface JoinRequestApprovedEvent {
    status: 'APPROVED';
    token: string;
    roomName: string;
}

export interface JoinRequestDeniedEvent {
    status: 'DENIED';
    reason: string | null;
}

export type JoinRequestDecisionEvent =
    | JoinRequestApprovedEvent
    | JoinRequestDeniedEvent;

export interface SubscribeMeetingEventsOptions {
    signal: AbortSignal;
    onJoinRequest: (request: PendingJoinRequest) => void;
}

function apiUrl(path: string): string {
    if (!apiConfig.apiBaseUrl) {
        throw new Error(
            'SMISKI_API_BASE_URL is required for realtime meeting events.',
        );
    }
    return `${apiConfig.apiBaseUrl}/api/${apiConfig.apiVersion}${path}`;
}

function parseJson<T>(message: SseMessage): T {
    try {
        return JSON.parse(message.data) as T;
    } catch {
        throw new Error(`Invalid ${message.event} event payload.`);
    }
}

function waitForRetry(signal: AbortSignal, delayMs = RECONNECT_DELAY_MS) {
    return new Promise<void>((resolve) => {
        if (signal.aborted) {
            resolve();
            return;
        }
        const finish = () => {
            window.clearTimeout(timeout);
            signal.removeEventListener('abort', finish);
            resolve();
        };
        const timeout = window.setTimeout(finish, delayMs);
        signal.addEventListener('abort', finish, { once: true });
    });
}

function isAbort(error: unknown, signal: AbortSignal): boolean {
    return (
        signal.aborted
        || (error instanceof DOMException && error.name === 'AbortError')
    );
}

/** Keeps a host stream connected and replays pending requests after reconnects. */
export async function subscribeToMeetingJoinRequests(
    meetingId: string,
    options: SubscribeMeetingEventsOptions,
): Promise<void> {
    const url = apiUrl(`/meetings/${encodeURIComponent(meetingId)}/events`);

    while (!options.signal.aborted) {
        try {
            await consumeSse(url, {
                signal: options.signal,
                onMessage: (message) => {
                    if (message.event !== 'join_request_created') {
                        return undefined;
                    }
                    const payload = parseJson<{
                        requestId: string;
                        accountId: string;
                        displayName: string;
                        requestedAt?: string;
                        expiresAt?: string;
                        avatarUrl?: string | null;
                    }>(message);
                    options.onJoinRequest({
                        requestId: payload.requestId,
                        accountId: payload.accountId,
                        displayName: payload.displayName,
                        status: 'PENDING',
                        requestedAt: payload.requestedAt ?? '',
                        expiresAt: payload.expiresAt ?? '',
                        avatarUrl: payload.avatarUrl ?? '',
                    });
                    return undefined;
                },
            });
        } catch (error) {
            if (!isAbort(error, options.signal)) {
                await waitForRetry(options.signal);
            }
            continue;
        }
        await waitForRetry(options.signal);
    }
}

function decisionFromMessage(
    message: SseMessage,
): JoinRequestDecisionEvent | null {
    if (message.event === 'join_request_approved') {
        const payload = parseJson<{ token?: string; roomName?: string }>(
            message,
        );
        if (!payload.token || !payload.roomName) {
            throw new Error('Approved join event is missing its room token.');
        }
        return {
            status: 'APPROVED',
            token: payload.token,
            roomName: payload.roomName,
        };
    }
    if (message.event === 'join_request_denied') {
        const payload = parseJson<{ reason?: string | null }>(message);
        return { status: 'DENIED', reason: payload.reason ?? null };
    }
    return null;
}

/** Waits for the terminal decision of one participant join request. */
export async function waitForJoinRequestDecision(
    meetingId: string,
    requestId: string,
    signal: AbortSignal,
): Promise<JoinRequestDecisionEvent> {
    const url = apiUrl(
        `/meetings/${encodeURIComponent(meetingId)}`
            + `/join-requests/${encodeURIComponent(requestId)}/events`,
    );
    const expiresAt = Date.now() + JOIN_DECISION_TIMEOUT_MS;

    while (!signal.aborted && Date.now() < expiresAt) {
        let decision: JoinRequestDecisionEvent | null = null;
        try {
            await consumeSse(url, {
                signal,
                onMessage: (message) => {
                    decision = decisionFromMessage(message);
                    return decision ? false : undefined;
                },
            });
            if (decision) return decision;
        } catch (error) {
            if (isAbort(error, signal)) throw error;
        }
        await waitForRetry(signal);
    }

    if (signal.aborted)
        throw new DOMException('Join request aborted.', 'AbortError');
    throw new Error(
        'Timed out waiting for the host to approve the join request.',
    );
}
