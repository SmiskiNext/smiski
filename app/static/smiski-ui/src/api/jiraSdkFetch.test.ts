import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestJiraMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    requestJira: (...args: unknown[]) => requestJiraMock(...args),
}));

import { jiraCallOptions } from './jiraSdkFetch';

const ROUTE = 'https://jira.invalid/rest/api/3/permissions';

function forwardedInit(): {
    method?: string;
    headers?: Record<string, string>;
    body?: string;
} {
    return requestJiraMock.mock.calls[0][1];
}

describe('jiraSdkFetch', () => {
    beforeEach(() => {
        requestJiraMock.mockReset();
        requestJiraMock.mockResolvedValue(new Response('{}', { status: 200 }));
    });

    it('forwards a header the SDK operation set', async () => {
        await jiraCallOptions.fetch(
            new Request(ROUTE, {
                headers: { 'If-None-Match': '"catalogue-v1"' },
            }),
        );

        expect(forwardedInit().headers).toMatchObject({
            'if-none-match': '"catalogue-v1"',
        });
    });

    it('defaults Accept when the caller set none', async () => {
        await jiraCallOptions.fetch(new Request(ROUTE));

        expect(forwardedInit().headers).toMatchObject({
            Accept: 'application/json',
        });
    });

    it('keeps an Accept the caller chose instead of overriding it', async () => {
        await jiraCallOptions.fetch(
            new Request(ROUTE, { headers: { Accept: 'text/plain' } }),
        );

        const headers = forwardedInit().headers ?? {};
        expect(headers.accept).toBe('text/plain');
        expect(headers.Accept).toBeUndefined();
    });

    it('keeps forwarding the path and query', async () => {
        await jiraCallOptions.fetch(
            new Request(`${ROUTE}?permissions=view,edit`),
        );

        expect(requestJiraMock.mock.calls[0][0]).toBe(
            '/rest/api/3/permissions?permissions=view,edit',
        );
    });

    it('omits a body on a read', async () => {
        await jiraCallOptions.fetch(new Request(ROUTE));

        expect(forwardedInit().body).toBeUndefined();
    });

    it('forwards a body on a write', async () => {
        await jiraCallOptions.fetch(
            new Request(ROUTE, {
                method: 'POST',
                body: '{"a":1}',
                headers: { 'Content-Type': 'application/json' },
            }),
        );

        expect(forwardedInit().body).toBe('{"a":1}');
    });
});
