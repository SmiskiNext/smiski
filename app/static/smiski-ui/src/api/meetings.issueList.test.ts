import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

import { listIssueMeetings } from './meetings';

function invokeResult(status: number, body: unknown) {
    return {
        status,
        headers: { 'content-type': 'application/json' },
        body,
    };
}

describe('listIssueMeetings', () => {
    beforeEach(() => invokeRemoteMock.mockReset());

    it('uses the dedicated issue-id endpoint and preserves offset metadata', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, {
                data: [
                    {
                        id: '0195e0c2-8f3a-7c21-b9d4-000000000001',
                        hostId: 'host-1',
                        title: 'Issue meeting',
                        issueId: '10001',
                        issueKey: 'SMISKI-1',
                        projectKey: 'SMISKI',
                        type: 'SCHEDULED',
                        status: 'SCHEDULED',
                    },
                ],
                meta: {
                    total: 21,
                    offset: 20,
                    pageSize: 20,
                    hasNext: false,
                },
            }),
        );

        const page = await listIssueMeetings('10001', {
            offset: 20,
            pageSize: 20,
        });

        expect(invokeRemoteMock).toHaveBeenCalledWith(
            expect.objectContaining({
                path: '/api/1/issues/10001/meetings',
                method: 'POST',
                body: { offset: 20, pageSize: 20 },
            }),
        );
        expect(page).toMatchObject({
            total: 21,
            offset: 20,
            pageSize: 20,
            hasNext: false,
        });
        expect(page.meetings).toHaveLength(1);
    });
});
