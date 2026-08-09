import { beforeEach, describe, expect, it, vi } from 'vitest';
import { forgeResponse } from './__tests__/forgeRemoteTestDouble';

const invokeRemoteMock = vi.fn();
vi.mock('@forge/api', () => ({
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
}));

const { forgeApiRemoteFetch } = await import('./forgeApiRemoteFetch');

const BASE_URL = 'https://forge-remote.invalid';

function jsonRequest(path: string, body: unknown): Request {
    return new Request(`${BASE_URL}${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
}

function lastInvocation(): {
    path: string;
    method: string;
    headers: Record<string, string>;
    body?: string;
} {
    return invokeRemoteMock.mock.calls[0][1];
}

describe('forgeApiRemoteFetch (server-side SDK transport over invokeRemote)', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
    });

    it('calls invokeRemote with the meet-backend remote key', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}));

        await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'DELETE' }),
        );

        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
        expect(invokeRemoteMock.mock.calls[0][0]).toBe('meet-backend');
    });

    it('forwards the pathname and search string', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}));

        await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants?foo=bar`, {
                method: 'GET',
            }),
        );

        expect(lastInvocation().path).toBe('/api/1/tenants?foo=bar');
    });

    it('forwards the method', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}));

        await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'DELETE' }),
        );

        expect(lastInvocation().method).toBe('DELETE');
    });

    it('forwards the SDK-composed headers only, injecting no context or identity header', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}));

        await forgeApiRemoteFetch(
            jsonRequest('/api/1/tenants', { id: 'install-1' }),
        );

        const { headers } = lastInvocation();
        expect(headers['content-type']).toBe('application/json');
        expect(headers).not.toHaveProperty('x-issue-id');
        expect(headers).not.toHaveProperty('x-project-id');
        expect(headers).not.toHaveProperty('x-tenant-id');
        expect(headers).not.toHaveProperty('x-account-id');
    });

    it('forwards a JSON body as already-serialized text', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}));

        await forgeApiRemoteFetch(
            jsonRequest('/api/1/tenants', { id: 'install-1' }),
        );

        expect(lastInvocation().body).toBe(JSON.stringify({ id: 'install-1' }));
    });

    it('omits the body entirely when the request carries none', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}));

        await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'DELETE' }),
        );

        expect('body' in lastInvocation()).toBe(false);
    });

    it('returns a 2xx response with its status, headers and body intact', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(
                201,
                { installationId: 'install-1' },
                { 'content-type': 'application/json', 'x-trace': 'trace-9' },
            ),
        );

        const response = await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'POST' }),
        );

        expect(response.ok).toBe(true);
        expect(response.status).toBe(201);
        expect(response.headers.get('x-trace')).toBe('trace-9');
        expect(await response.json()).toEqual({ installationId: 'install-1' });
    });

    it('preserves status and Problem Details on a non-2xx response', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(404, {
                type: 'about:blank',
                title: 'Tenant Not Found',
                status: 404,
                code: 'TENANT_NOT_FOUND',
                traceId: 'trace-1',
            }),
        );

        const response = await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'DELETE' }),
        );

        expect(response.ok).toBe(false);
        expect(response.status).toBe(404);
        expect(await response.json()).toMatchObject({
            code: 'TENANT_NOT_FOUND',
            traceId: 'trace-1',
        });
    });

    it('defaults Content-Type to application/json when the platform sends none', async () => {
        invokeRemoteMock.mockResolvedValue(forgeResponse(200, {}, {}));

        const response = await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'POST' }),
        );

        expect(response.headers.get('Content-Type')).toBe('application/json');
    });

    it('drops a malformed header name instead of throwing a raw TypeError', async () => {
        invokeRemoteMock.mockResolvedValue(
            forgeResponse(
                200,
                { installationId: 'install-1' },
                {
                    'content-type': 'application/json',
                    'x-trace id': 'trace-9',
                },
            ),
        );

        const response = await forgeApiRemoteFetch(
            new Request(`${BASE_URL}/api/1/tenants`, { method: 'POST' }),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ installationId: 'install-1' });
    });

    it('surfaces a rejected invocation as a rejected promise', async () => {
        invokeRemoteMock.mockRejectedValue(
            new Error('Remote could not verify the Forge Invocation Token'),
        );

        await expect(
            forgeApiRemoteFetch(
                new Request(`${BASE_URL}/api/1/tenants`, { method: 'POST' }),
            ),
        ).rejects.toThrow('Remote could not verify the Forge Invocation Token');
    });
});
