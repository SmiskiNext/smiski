import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

import { listAllMeetings, listProjectMeetings } from './meetings';

function invokeResult(status: number, body: unknown) {
    return {
        status,
        headers: { 'content-type': 'application/json' },
        body,
    };
}

function meetingSummary(index: number) {
    return {
        id: `0195e0c2-8f3a-7c21-b9d4-${String(index).padStart(12, '0')}`,
        hostId: 'acc-host-1',
        title: `Project meeting ${index}`,
        description: 'Daily sync',
        issueKey: 'SMISKI-10',
        projectKey: 'SMISKI',
        type: 'SCHEDULED',
        status: 'SCHEDULED',
        startTime: '2026-08-05T09:00:00Z',
        createdAt: '2026-08-04T10:00:00Z',
    };
}

function lastInvocation() {
    return invokeRemoteMock.mock.calls.at(-1)?.[0];
}

describe('listProjectMeetings', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
    });

    it('sends project filters and cursor controls to the list operation', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                data: [meetingSummary(1)],
                meta: {
                    size: 1,
                    hasNext: true,
                    nextPageToken: 'next-page',
                },
            }),
        );

        const page = await listProjectMeetings({
            projectKey: 'SMISKI',
            createdByAccountId: 'acc-host-1',
            status: 'SCHEDULED',
            search: 'Standup',
            sort: 'START_TIME',
            pageSize: 20,
            pageToken: 'current-page',
        });

        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
        expect(lastInvocation()).toMatchObject({
            path: '/api/1/meetings',
            method: 'POST',
            body: {
                projectKey: 'SMISKI',
                creatorId: 'acc-host-1',
                statuses: ['SCHEDULED'],
                search: 'Standup',
                sort: 'START_TIME',
                pageSize: 20,
                pageToken: 'current-page',
            },
        });
        expect(page).toMatchObject({
            size: 1,
            hasNext: true,
            nextPageToken: 'next-page',
        });
        expect(page.meetings[0]).toMatchObject({
            title: 'Project meeting 1',
            projectKey: 'SMISKI',
        });
    });

    it('handles an empty terminal page', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                data: [],
                meta: { size: 0, hasNext: false },
            }),
        );

        await expect(
            listProjectMeetings({ projectKey: 'PROJ' }),
        ).resolves.toEqual({
            meetings: [],
            size: 0,
            hasNext: false,
            nextPageToken: undefined,
        });
    });

    it('loads every cursor page for checks spanning more than 20 meetings', async () => {
        invokeRemoteMock
            .mockResolvedValueOnce(
                invokeResult(200, {
                    data: Array.from({ length: 20 }, (_, index) =>
                        meetingSummary(index + 1),
                    ),
                    meta: {
                        size: 20,
                        hasNext: true,
                        nextPageToken: 'page-2',
                    },
                }),
            )
            .mockResolvedValueOnce(
                invokeResult(200, {
                    data: [meetingSummary(21)],
                    meta: { size: 1, hasNext: false },
                }),
            );

        const meetings = await listAllMeetings({ projectKey: 'SMISKI' });

        expect(meetings).toHaveLength(21);
        expect(invokeRemoteMock).toHaveBeenCalledTimes(2);
        expect(invokeRemoteMock.mock.calls[0][0].body).toMatchObject({
            projectKey: 'SMISKI',
            pageSize: 50,
        });
        expect(invokeRemoteMock.mock.calls[1][0].body).toMatchObject({
            projectKey: 'SMISKI',
            pageSize: 50,
            pageToken: 'page-2',
        });
    });

    it('rejects a non-terminal page without a next cursor', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                data: [meetingSummary(1)],
                meta: { size: 1, hasNext: true },
            }),
        );

        await expect(
            listProjectMeetings({ projectKey: 'SMISKI' }),
        ).rejects.toMatchObject({ code: 'INVALID_PAGINATION_RESPONSE' });
    });

    it('stops when the backend repeats a cursor', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                data: [meetingSummary(1)],
                meta: {
                    size: 1,
                    hasNext: true,
                    nextPageToken: 'repeated-page',
                },
            }),
        );

        await expect(
            listAllMeetings({ projectKey: 'SMISKI' }),
        ).rejects.toMatchObject({ code: 'INVALID_PAGINATION_RESPONSE' });
        expect(invokeRemoteMock).toHaveBeenCalledTimes(2);
    });
});
