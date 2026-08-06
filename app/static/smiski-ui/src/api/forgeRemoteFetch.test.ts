import { beforeEach, describe, expect, it, vi } from 'vitest';

const invokeRemoteMock = vi.fn();
const requestJiraMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    invoke: vi.fn(),
    invokeRemote: (...args: unknown[]) => invokeRemoteMock(...args),
    requestJira: (...args: unknown[]) => requestJiraMock(...args),
}));

import {
    clearBackendContext,
    getBackendContext,
    publishProjectContext,
    resolveProjectId,
    setBackendContext,
} from './backendContext';
import { forgeRemoteFetch } from './forgeRemoteFetch';

const BASE_URL = 'https://forge-remote.invalid';

function invokeResult(
    status: number,
    body?: unknown,
    headers: Record<string, string> = { 'content-type': 'application/json' },
) {
    return { status, headers, body };
}

function jsonRequest(path: string, body: unknown): Request {
    return new Request(`${BASE_URL}${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
}

function lastInvocation() {
    return invokeRemoteMock.mock.calls[0][0];
}

describe('forgeRemoteFetch (SDK transport over invokeRemote)', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
        requestJiraMock.mockReset();
        clearBackendContext();
    });

    it('issues the call through invokeRemote with no remote key, so the platform attaches the system token', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(200, { ok: true }));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(invokeRemoteMock).toHaveBeenCalledTimes(1);
        expect(invokeRemoteMock.mock.calls[0]).toHaveLength(1);
        expect(lastInvocation()).toMatchObject({
            path: '/api/1/meetings/abc',
            method: 'GET',
        });
    });

    it('forwards the path with its query string', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings?offset=2&pageSize=1`, {
                method: 'GET',
            }),
        );

        expect(lastInvocation().path).toBe(
            '/api/1/meetings?offset=2&pageSize=1',
        );
    });

    it('passes a JSON body as an object so Forge does not double-encode it', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(201, {}));

        await forgeRemoteFetch(
            jsonRequest('/api/1/meetings:instant', {
                title: 'Incident sync',
                settings: { admissionPolicy: 'ALLOW_ALL' },
            }),
        );

        const { body } = lastInvocation();
        expect(typeof body).toBe('object');
        expect(body).toEqual({
            title: 'Incident sync',
            settings: { admissionPolicy: 'ALLOW_ALL' },
        });
    });

    it('omits the body entirely when the request carries none', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect('body' in lastInvocation()).toBe(false);
    });

    it('returns a 2xx response with its status, headers and body intact', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(
                200,
                { id: 'meeting-1', title: 'Sync' },
                { 'content-type': 'application/json', 'x-trace': 'trace-9' },
            ),
        );

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.ok).toBe(true);
        expect(response.status).toBe(200);
        expect(response.headers.get('Content-Type')).toBe('application/json');
        expect(response.headers.get('x-trace')).toBe('trace-9');
        expect(await response.json()).toEqual({
            id: 'meeting-1',
            title: 'Sync',
        });
    });

    it('passes a string response body through without double-encoding it', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(200, JSON.stringify({ id: 'm1' })),
        );

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/m1`, { method: 'GET' }),
        );

        expect(await response.json()).toEqual({ id: 'm1' });
    });

    it('resolves a 204 No Content with an empty representation and no parse error', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(204, undefined, {}));

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, {
                method: 'DELETE',
            }),
        );

        expect(response.ok).toBe(true);
        expect(response.status).toBe(204);
        await expect(response.text()).resolves.toBe('');
    });

    it('preserves code, traceId, status and detail on a non-2xx response', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(403, {
                type: 'about:blank',
                title: 'Forbidden',
                status: 403,
                detail: 'Only the host may change the meeting settings',
                code: 'NOT_AUTHORIZED',
                traceId: 'trace-1',
            }),
        );

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.ok).toBe(false);
        expect(response.status).toBe(403);
        expect(await response.json()).toMatchObject({
            code: 'NOT_AUTHORIZED',
            traceId: 'trace-1',
            status: 403,
            detail: 'Only the host may change the meeting settings',
        });
    });

    it('surfaces a rejected invocation as an error with a readable message', async () => {
        invokeRemoteMock.mockRejectedValue(
            new Error('Remote could not verify the Forge Invocation Token'),
        );

        await expect(
            forgeRemoteFetch(
                new Request(`${BASE_URL}/api/1/meetings/abc`, {
                    method: 'GET',
                }),
            ),
        ).rejects.toThrow('Remote could not verify the Forge Invocation Token');
    });

    it('treats a failure reported as a resolved error payload as a failure', async () => {
        invokeRemoteMock.mockResolvedValue({
            message: 'Invalid response from remote',
        });

        await expect(
            forgeRemoteFetch(
                new Request(`${BASE_URL}/api/1/meetings/abc`, {
                    method: 'GET',
                }),
            ),
        ).rejects.toThrow('Invalid response from remote');
    });

    it('treats a resolved payload carrying no status as a failure even without a message', async () => {
        invokeRemoteMock.mockResolvedValue({});

        await expect(
            forgeRemoteFetch(
                new Request(`${BASE_URL}/api/1/meetings/abc`, {
                    method: 'GET',
                }),
            ),
        ).rejects.toThrow('The meeting backend could not be reached.');
    });

    it('unwraps a { body, metadata } envelope rather than reading it as a failure', async () => {
        invokeRemoteMock.mockResolvedValue({
            body: invokeResult(200, { id: 'meeting-1' }),
            metadata: { rateLimitProperties: { rateLimitRemaining: 499 } },
        });

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ id: 'meeting-1' });
    });

    it('unwraps an envelope carrying no metadata, since metadata is optional', async () => {
        invokeRemoteMock.mockResolvedValue({
            body: invokeResult(200, { id: 'meeting-1' }),
        });

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ id: 'meeting-1' });
    });

    it('does not unwrap a bare response whose body is a Problem Details carrying a status', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(403, {
                type: 'about:blank',
                title: 'Forbidden',
                status: 403,
                detail: 'Only the host may change the meeting settings',
                code: 'NOT_AUTHORIZED',
                traceId: 'trace-1',
            }),
        );

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.status).toBe(403);
        expect(await response.json()).toMatchObject({
            code: 'NOT_AUTHORIZED',
            traceId: 'trace-1',
        });
    });

    it('surfaces an out-of-range status as a readable failure instead of a RangeError', async () => {
        invokeRemoteMock.mockResolvedValue({
            status: 0,
            headers: {},
        });

        await expect(
            forgeRemoteFetch(
                new Request(`${BASE_URL}/api/1/meetings/abc`, {
                    method: 'GET',
                }),
            ),
        ).rejects.toThrow('The meeting backend could not be reached.');
    });

    it('drops a stale content-length so it cannot contradict the rebuilt body', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(
                200,
                { id: 'meeting-1' },
                { 'content-type': 'application/json', 'content-length': '0' },
            ),
        );

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.headers.get('Content-Length')).toBeNull();
        expect(await response.json()).toEqual({ id: 'meeting-1' });
    });

    it('drops a malformed header name instead of throwing a raw TypeError', async () => {
        invokeRemoteMock.mockResolvedValue(
            invokeResult(
                200,
                { id: 'meeting-1' },
                {
                    'content-type': 'application/json',
                    'x-trace id': 'trace-9',
                },
            ),
        );

        const response = await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(response.status).toBe(200);
        expect(response.headers.get('Content-Type')).toBe('application/json');
        expect(await response.json()).toEqual({ id: 'meeting-1' });
    });

    it('surfaces an unreadable response as a readable failure, not a raw TypeError', async () => {
        invokeRemoteMock.mockResolvedValue({
            status: 200,
            statusText: 'Not\nAllowed',
            headers: { 'content-type': 'application/json' },
            body: {},
        });

        await expect(
            forgeRemoteFetch(
                new Request(`${BASE_URL}/api/1/meetings/abc`, {
                    method: 'GET',
                }),
            ),
        ).rejects.toThrow(
            'The meeting backend returned a response that could not be read.',
        );
    });

    it('omits a body that is not JSON rather than letting Forge double-encode it', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: 'not json at all',
            }),
        );

        expect('body' in lastInvocation()).toBe(false);
    });

    it('injects x-issue-id and x-project-id from the issue context', async () => {
        setBackendContext({ issueId: '10001', projectId: '10002' });
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(lastInvocation().headers).toMatchObject({
            'x-issue-id': '10001',
            'x-project-id': '10002',
        });
    });

    it('renders a numeric identifier as a string', async () => {
        setBackendContext({ issueId: 10001, projectId: 10002 });
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(lastInvocation().headers['x-issue-id']).toBe('10001');
        expect(lastInvocation().headers['x-project-id']).toBe('10002');
    });

    it('omits both headers when no context is available', async () => {
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        const { headers } = lastInvocation();
        expect(headers).not.toHaveProperty('x-issue-id');
        expect(headers).not.toHaveProperty('x-project-id');
    });

    it('omits x-project-id when the project exposes only a key', async () => {
        setBackendContext({ issueId: '10001', projectId: undefined });
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        expect(lastInvocation().headers['x-issue-id']).toBe('10001');
        expect(lastInvocation().headers).not.toHaveProperty('x-project-id');
    });

    it('never sends a Jira key or a key-derived synthetic identifier', async () => {
        setBackendContext({
            issueId: 'SMISKI-101',
            projectId: 'project-smiski',
        });
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        const { headers } = lastInvocation();
        expect(headers).not.toHaveProperty('x-issue-id');
        expect(headers).not.toHaveProperty('x-project-id');
    });

    it('does not overwrite caller-supplied headers', async () => {
        setBackendContext({ issueId: '10001', projectId: '10002' });
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'x-issue-id': '99999',
                },
                body: JSON.stringify({ title: 'Sync' }),
            }),
        );

        const { headers } = lastInvocation();
        expect(headers['content-type']).toBe('application/json');
        expect(headers['x-issue-id']).toBe('99999');
        expect(headers['x-project-id']).toBe('10002');
    });

    it('asserts no tenant or account identity headers of its own', async () => {
        setBackendContext({ issueId: '10001', projectId: '10002' });
        invokeRemoteMock.mockResolvedValue(invokeResult(200, {}));

        await forgeRemoteFetch(
            new Request(`${BASE_URL}/api/1/meetings/abc`, { method: 'GET' }),
        );

        const names = Object.keys(lastInvocation().headers).map((name) =>
            name.toLowerCase(),
        );
        expect(names).not.toContain('x-tenant-id');
        expect(names).not.toContain('x-account-id');
    });

    it('carries the opener identifiers into a request issued from a modal surface', async () => {
        setBackendContext({ issueId: '10001', projectId: '10002' });

        const modalPayload = {
            kind: 'instant-meeting',
            issueKey: 'SMISKI-101',
            issueId: '10001',
            projectId: '10002',
        };

        clearBackendContext();
        setBackendContext(modalPayload);
        invokeRemoteMock.mockResolvedValue(invokeResult(201, {}));

        await forgeRemoteFetch(
            jsonRequest('/api/1/meetings:instant', { title: 'From modal' }),
        );

        expect(lastInvocation().headers).toMatchObject({
            'x-issue-id': '10001',
            'x-project-id': '10002',
        });
    });
});

describe('resolveProjectId', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
        requestJiraMock.mockReset();
        clearBackendContext();
    });

    it('resolves the numeric project identifier from a project key', async () => {
        requestJiraMock.mockResolvedValue(
            new Response(JSON.stringify({ id: '10002', key: 'SMISKI' }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }),
        );

        await expect(resolveProjectId('SMISKI')).resolves.toBe('10002');
        expect(requestJiraMock).toHaveBeenCalledWith(
            '/rest/api/3/project/SMISKI',
            expect.objectContaining({
                headers: { Accept: 'application/json' },
            }),
        );
    });

    it('caches the resolution so a project is looked up once', async () => {
        requestJiraMock.mockResolvedValue(
            new Response(JSON.stringify({ id: '10002' }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }),
        );

        await resolveProjectId('SMISKI');
        await resolveProjectId('SMISKI');

        expect(requestJiraMock).toHaveBeenCalledTimes(1);
    });

    it('yields no identifier when Jira denies the lookup, so the header is omitted', async () => {
        requestJiraMock.mockResolvedValue(new Response('', { status: 403 }));

        await expect(resolveProjectId('SMISKI')).resolves.toBeUndefined();
    });

    it('yields no identifier when the lookup throws outside the Forge iframe', async () => {
        requestJiraMock.mockRejectedValue(new Error('no bridge'));

        await expect(resolveProjectId('SMISKI')).resolves.toBeUndefined();
    });
});

describe('publishProjectContext', () => {
    beforeEach(() => {
        invokeRemoteMock.mockReset();
        requestJiraMock.mockReset();
        clearBackendContext();
        vi.useRealTimers();
    });

    it('publishes the context identifier without asking Jira', async () => {
        await publishProjectContext('10002', 'SMISKI');

        expect(getBackendContext().projectId).toBe('10002');
        expect(requestJiraMock).not.toHaveBeenCalled();
    });

    it('preserves an already-published issue identifier', async () => {
        setBackendContext({ issueId: '10001', projectId: undefined });

        await publishProjectContext('10002', 'SMISKI');

        expect(getBackendContext()).toEqual({
            issueId: '10001',
            projectId: '10002',
        });
    });

    it('preserves the issue identifier even when the project cannot be resolved', async () => {
        setBackendContext({ issueId: '10001', projectId: undefined });
        requestJiraMock.mockResolvedValue(new Response('', { status: 403 }));

        await publishProjectContext(undefined, 'SMISKI');

        expect(getBackendContext().issueId).toBe('10001');
        expect(getBackendContext().projectId).toBeUndefined();
    });

    it('resolves the identifier from the key before settling when the context omits it', async () => {
        requestJiraMock.mockResolvedValue(
            new Response(JSON.stringify({ id: '10002' }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }),
        );

        await publishProjectContext(undefined, 'SMISKI');

        expect(getBackendContext().projectId).toBe('10002');
    });

    it('settles with no identifier when the project cannot be resolved', async () => {
        requestJiraMock.mockResolvedValue(new Response('', { status: 403 }));

        await publishProjectContext(undefined, 'SMISKI');

        expect(getBackendContext().projectId).toBeUndefined();
    });

    it('settles within its budget rather than hanging on a stalled lookup', async () => {
        vi.useFakeTimers();
        requestJiraMock.mockReturnValue(new Promise(() => {}));

        const published = publishProjectContext(undefined, 'SMISKI');
        await vi.advanceTimersByTimeAsync(5_000);
        await published;

        expect(getBackendContext().projectId).toBeUndefined();
    });

    it('warns naming the project key when the budget elapses without an identifier', async () => {
        vi.useFakeTimers();
        const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
        requestJiraMock.mockReturnValue(new Promise(() => {}));

        const published = publishProjectContext(undefined, 'SMISKI');
        await vi.advanceTimersByTimeAsync(5_000);
        await published;

        expect(warn).toHaveBeenCalledTimes(1);
        expect(warn.mock.calls[0][0]).toContain('SMISKI');
        warn.mockRestore();
    });

    it('does not warn when the lookup resolves within its budget', async () => {
        const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
        requestJiraMock.mockResolvedValue(
            new Response(JSON.stringify({ id: '10002' }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }),
        );

        await publishProjectContext(undefined, 'SMISKI');

        expect(warn).not.toHaveBeenCalled();
        warn.mockRestore();
    });
});
