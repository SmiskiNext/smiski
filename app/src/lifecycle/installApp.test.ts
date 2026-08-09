import { beforeEach, describe, expect, it, vi } from 'vitest';
import { forgeResponse } from '../api/__tests__/forgeRemoteTestDouble';
import type { AppInstallationEvent } from './events';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/api', () => ({
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

const { recordInstallation } = await import('./installApp');

const INSTALLATION_ID = 'fff8e466-31f4-4c73-a337-c3309dd930dc';
const APP_ID = '406d303d-0393-4ec4-ad7c-1435be94583a';
const ENVIRONMENT_ID = '23863033-1de4-4ebf-b30d-c906264a1e92';

function installedEvent(
    overrides: Partial<AppInstallationEvent> = {},
): AppInstallationEvent {
    return {
        id: INSTALLATION_ID,
        installerAccountId: '4ad9aa0c52dc1b420a791d12',
        app: {
            id: APP_ID,
            version: '9.0.0',
            name: 'Smiski Meetings',
            ownerAccountId: '3bc8aa0c52dc1b310a791d34',
        },
        environment: { id: ENVIRONMENT_ID },
        ...overrides,
    };
}

function tenantSnapshot(status: 'ACTIVE' = 'ACTIVE') {
    return {
        installationId: INSTALLATION_ID,
        appId: APP_ID,
        appVersion: '9.0.0',
        environmentId: ENVIRONMENT_ID,
        status,
        installedAt: '2026-08-09T10:00:00Z',
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

function sentBody(): unknown {
    const { body } = lastInvocation();
    return body === undefined ? undefined : JSON.parse(body);
}

describe('recordInstallation (install and upgrade → SDK register)', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
    });

    it('records the tenant through register() over Forge Remote and returns the snapshot from result data', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(201, tenantSnapshot()),
        );

        const snapshot = await recordInstallation(installedEvent());

        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
        expect(invokeRemoteMock.mock.calls[0][0]).toBe('meet-backend');
        expect(lastInvocation()).toMatchObject({
            path: '/api/1/tenants',
            method: 'POST',
        });
        expect(snapshot).toMatchObject({
            installationId: INSTALLATION_ID,
            status: 'ACTIVE',
        });
    });

    it('maps the install payload onto the register request body', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(201, tenantSnapshot()),
        );

        await recordInstallation(installedEvent());

        expect(sentBody()).toEqual({
            id: INSTALLATION_ID,
            installerAccountId: '4ad9aa0c52dc1b420a791d12',
            app: {
                id: APP_ID,
                version: '9.0.0',
                name: 'Smiski Meetings',
                ownerAccountId: '3bc8aa0c52dc1b310a791d34',
            },
            environment: { id: ENVIRONMENT_ID },
        });
    });

    it('calls the same register() for a major upgrade and treats 200 as success', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(200, tenantSnapshot()),
        );

        const upgradeEvent = installedEvent({
            installerAccountId: undefined,
            upgraderAccountId: '4ad9aa0c52dc1b420a791d12',
            app: { id: APP_ID, version: '10.0.0' },
        });

        const snapshot = await recordInstallation(upgradeEvent);

        expect(lastInvocation()).toMatchObject({
            path: '/api/1/tenants',
            method: 'POST',
        });
        expect(sentBody()).toEqual({
            id: INSTALLATION_ID,
            installerAccountId: '4ad9aa0c52dc1b420a791d12',
            app: { id: APP_ID, version: '10.0.0' },
            environment: { id: ENVIRONMENT_ID },
        });
        expect(snapshot.installationId).toBe(INSTALLATION_ID);
    });

    it('omits the environment when the event carries none', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(201, tenantSnapshot()),
        );

        await recordInstallation(installedEvent({ environment: undefined }));

        expect(sentBody()).not.toHaveProperty('environment');
    });

    it('omits the account when neither installer nor upgrader is named', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(201, tenantSnapshot()),
        );

        await recordInstallation(
            installedEvent({ installerAccountId: undefined }),
        );

        expect(sentBody()).not.toHaveProperty('installerAccountId');
    });

    it('asserts no identity and no context headers on the register request', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(201, tenantSnapshot()),
        );

        await recordInstallation(installedEvent());

        const { headers } = lastInvocation();
        expect(headers).not.toHaveProperty('x-tenant-id');
        expect(headers).not.toHaveProperty('x-account-id');
        expect(headers).not.toHaveProperty('x-issue-id');
        expect(headers).not.toHaveProperty('x-project-id');
    });

    it('throws on a non-success response so Forge retries the event', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(500, {
                type: 'about:blank',
                title: 'Internal Server Error',
                status: 500,
                code: 'INTERNAL_ERROR',
                traceId: 'trace-7',
            }),
        );

        await expect(recordInstallation(installedEvent())).rejects.toThrow(
            /INTERNAL_ERROR/,
        );
    });

    it('throws when the backend is unreachable so Forge retries the event', async () => {
        invokeRemoteMock.mockRejectedValue(
            new Error('Remote could not be reached'),
        );

        await expect(recordInstallation(installedEvent())).rejects.toThrow(
            /Remote could not be reached/,
        );
    });
});
