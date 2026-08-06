import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    requestRemote: (...args: unknown[]) => requestRemoteMock(...args),
}));

import { listProjectMeetings } from './meetings';

function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

describe('listProjectMeetings (SDK list operation over Forge Remote with projectKey)', () => {
    beforeEach(() => {
        requestRemoteMock.mockReset();
    });

    it('sends projectKey and optional filters to backend POST /api/1/meetings', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                data: [
                    {
                        id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
                        hostId: 'acc-host-1',
                        title: 'Project Standup',
                        description: 'Daily sync',
                        issueKey: 'SMISKI-10',
                        projectKey: 'SMISKI',
                        type: 'SCHEDULED',
                        status: 'SCHEDULED',
                        startTime: '2026-08-05T09:00:00Z',
                        createdAt: '2026-08-04T10:00:00Z',
                    },
                ],
                meta: { size: 1, hasNext: false },
            }),
        );

        const meetings = await listProjectMeetings({
            projectKey: 'SMISKI',
            createdByAccountId: 'acc-host-1',
            status: 'SCHEDULED',
            search: 'Standup',
        });

        expect(requestRemoteMock).toHaveBeenCalledWith(
            'meet-backend',
            expect.objectContaining({
                path: '/api/1/meetings',
                method: 'POST',
            }),
        );

        const [, options] = requestRemoteMock.mock.calls[0];
        const body = JSON.parse(options.body);
        expect(body).toEqual({
            projectKey: 'SMISKI',
            creatorId: 'acc-host-1',
            statuses: ['SCHEDULED'],
            search: 'Standup',
        });

        expect(meetings).toHaveLength(1);
        expect(meetings[0]).toMatchObject({
            id: '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90',
            title: 'Project Standup',
            issueKey: 'SMISKI-10',
            projectKey: 'SMISKI',
            status: 'SCHEDULED',
        });
    });

    it('handles empty response list gracefully', async () => {
        requestRemoteMock.mockResolvedValue(
            jsonResponse(200, {
                data: [],
                meta: { size: 0, hasNext: false },
            }),
        );

        const meetings = await listProjectMeetings({ projectKey: 'PROJ' });
        expect(meetings).toEqual([]);
    });
});
