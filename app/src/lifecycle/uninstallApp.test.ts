import { beforeEach, describe, expect, it, vi } from 'vitest';
import { forgeResponse } from '../api/__tests__/forgeRemoteTestDouble';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/api', () => ({
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

const { recordUninstall } = await import('./uninstallApp');

function tenantSnapshot(status: 'ACTIVE' | 'UNINSTALLED') {
    return {
        installationId: 'fff8e466-31f4-4c73-a337-c3309dd930dc',
        appId: '406d303d-0393-4ec4-ad7c-1435be94583a',
        status,
        installedAt: '2026-08-01T10:00:00Z',
        updatedAt: '2026-08-09T10:00:00Z',
    };
}

function lastInvocation(): {
    path: string;
    method: string;
    headers: Record<string, string>;
    body?: string;
} {
    return invokeRemoteMock.mock.calls[0][1];
}

describe('recordUninstall (preUninstall → SDK uninstall)', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
    });

    it('records the uninstall through uninstall() (DELETE) over Forge Remote and treats 200 as success', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(200, tenantSnapshot('UNINSTALLED')),
        );

        const outcome = await recordUninstall();

        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
        expect(invokeRemoteMock.mock.calls[0][0]).toBe('meet-backend');
        expect(lastInvocation()).toMatchObject({
            path: '/api/1/tenants',
            method: 'DELETE',
        });
        expect(outcome).toEqual({ status: 'uninstalled' });
    });

    it('asserts no identity and no context headers on the uninstall request', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(200, tenantSnapshot('UNINSTALLED')),
        );

        await recordUninstall();

        const { headers } = lastInvocation();
        expect(headers).not.toHaveProperty('x-tenant-id');
        expect(headers).not.toHaveProperty('x-account-id');
        expect(headers).not.toHaveProperty('x-issue-id');
        expect(headers).not.toHaveProperty('x-project-id');
    });

    it('treats a 404 (unknown tenant) as success without throwing', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(404, {
                type: 'about:blank',
                title: 'Tenant Not Found',
                status: 404,
                detail: 'No tenant exists for the given cloud ID',
                code: 'TENANT_NOT_FOUND',
                traceId: 'trace-3',
            }),
        );

        const outcome = await recordUninstall();

        expect(outcome).toEqual({ status: 'absent' });
    });

    it('treats a 200 for an already-uninstalled tenant as success without throwing', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(200, tenantSnapshot('UNINSTALLED')),
        );

        const outcome = await recordUninstall();

        expect(outcome).toEqual({ status: 'uninstalled' });
    });

    it('never throws on an unreachable backend, reporting a failed outcome instead', async () => {
        invokeRemoteMock.mockRejectedValue(
            new Error('Remote could not be reached'),
        );

        const outcome = await recordUninstall();

        expect(outcome.status).toBe('failed');
        if (outcome.status === 'failed') {
            expect(outcome.reason).toContain('Remote could not be reached');
        }
    });

    it('never throws on a non-success, non-404 response, reporting a failed outcome instead', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(500, {
                type: 'about:blank',
                title: 'Internal Server Error',
                status: 500,
                code: 'INTERNAL_ERROR',
                traceId: 'trace-8',
            }),
        );

        const outcome = await recordUninstall();

        expect(outcome.status).toBe('failed');
        if (outcome.status === 'failed') {
            expect(outcome.reason).toContain('INTERNAL_ERROR');
        }
    });
});
