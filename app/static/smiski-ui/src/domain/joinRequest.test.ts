import { describe, expect, it } from 'vitest';
import type {
    PendingJoinRequest,
    PendingJoinRequestsPage,
} from './joinRequest';
import {
    removePendingJoinRequestsFromPage,
    upsertPendingJoinRequestPage,
} from './joinRequest';

const REQUEST: PendingJoinRequest = {
    requestId: 'request-1',
    accountId: 'account-1',
    displayName: 'Alice',
    status: 'PENDING',
    requestedAt: '',
    expiresAt: '',
};

function page(requests: PendingJoinRequest[] = []): PendingJoinRequestsPage {
    return { requests, total: requests.length, offset: 0, pageSize: 20 };
}

describe('pending join-request cache updates', () => {
    it('prepends a new realtime request to the first page', () => {
        expect(upsertPendingJoinRequestPage(page(), REQUEST)).toEqual({
            requests: [REQUEST],
            total: 1,
            offset: 0,
            pageSize: 20,
        });
    });

    it('deduplicates replayed events and preserves REST-only timestamps', () => {
        const existing = {
            ...REQUEST,
            requestedAt: '2026-08-02T10:00:00Z',
            expiresAt: '2026-08-02T10:10:00Z',
        };

        const updated = upsertPendingJoinRequestPage(page([existing]), {
            ...REQUEST,
            displayName: 'Alice Updated',
        });

        expect(updated.total).toBe(1);
        expect(updated.requests).toEqual([
            { ...existing, displayName: 'Alice Updated' },
        ]);
    });

    it('does not corrupt an offset page when an event changes page boundaries', () => {
        const offsetPage = { ...page(), offset: 20 };

        expect(upsertPendingJoinRequestPage(offsetPage, REQUEST)).toBe(
            offsetPage,
        );
    });

    it('removes terminal requests and updates the total', () => {
        const other = { ...REQUEST, requestId: 'request-2' };

        expect(
            removePendingJoinRequestsFromPage(
                page([REQUEST, other]),
                new Set([REQUEST.requestId]),
            ),
        ).toEqual({
            requests: [other],
            total: 1,
            offset: 0,
            pageSize: 20,
        });
    });
});
