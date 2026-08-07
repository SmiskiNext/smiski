// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest';

const sdkMocks = vi.hoisted(() => ({
    getAllPermissions: vi.fn(),
    getMyPermissions: vi.fn(),
}));

vi.mock('@smiskinext/sdks-jira', () => sdkMocks);
vi.mock('@forge/bridge', () => ({ requestJira: vi.fn() }));

import {
    checkMeetingPermission,
    resolveMeetingPermissionKeys,
} from './meetingPermission';

const VIEW_KEY = 'app-abc__view-meeting';
const EDIT_KEY = 'app-abc__edit-meeting';
const ACCOUNT_ID = 'account-42';
const PROJECT_KEY = 'SMISKI';

const CATALOGUE = {
    permissions: {
        BROWSE_PROJECTS: { name: 'Browse Projects' },
        [VIEW_KEY]: { name: 'View Meeting' },
        [EDIT_KEY]: { name: 'Edit Meeting' },
    },
};

const GRANTED = {
    permissions: {
        [VIEW_KEY]: { havePermission: true },
        [EDIT_KEY]: { havePermission: true },
    },
};

const KEYS = { viewKey: VIEW_KEY, editKey: EDIT_KEY };

beforeEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
    localStorage.clear();
});

function ok(data: unknown, headers: Record<string, string> = {}) {
    return {
        data,
        response: new Response(JSON.stringify(data), { status: 200, headers }),
    };
}

/**
 * A `304` as the SDK client actually reports one. It treats any non-`ok`
 * response as a failure: the bodyless `304` makes it throw `''`, which its own
 * `finalError = finalError || {}` then turns into a truthy `{}`. The revalidation
 * has to recognise the status ahead of that error, so the error must be truthy
 * here or the test would pass without it.
 */
function notModified() {
    return { error: {}, response: new Response(null, { status: 304 }) };
}

function failed(status: number) {
    return {
        error: { errorMessages: ['Jira said no'] },
        response: new Response('{}', { status }),
    };
}

function headersOf(mock: { mock: { calls: unknown[][] } }, call: number) {
    const options = mock.mock.calls[call][0] as {
        headers?: Record<string, string>;
    };
    return options.headers ?? {};
}

describe('resolveMeetingPermissionKeys', () => {
    it('resolves the real Jira keys by matching the declared names', async () => {
        sdkMocks.getAllPermissions.mockResolvedValue(ok(CATALOGUE));

        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);
    });

    it('propagates a failure listing the site permissions', async () => {
        sdkMocks.getAllPermissions.mockResolvedValue(failed(403));

        await expect(resolveMeetingPermissionKeys()).rejects.toThrow(
            /Jira permission lookup failed while listing permissions/,
        );
    });

    it('propagates a rejected request rather than resolving empty', async () => {
        sdkMocks.getAllPermissions.mockRejectedValue(
            new Error('bridge unavailable'),
        );

        await expect(resolveMeetingPermissionKeys()).rejects.toThrow(
            'bridge unavailable',
        );
    });

    it('fails when the app permissions are not registered on the site', async () => {
        sdkMocks.getAllPermissions.mockResolvedValue(
            ok({ permissions: { BROWSE_PROJECTS: { name: 'Browse' } } }),
        );

        await expect(resolveMeetingPermissionKeys()).rejects.toThrow(
            /not registered on this Jira site yet/,
        );
    });
});

describe('checkMeetingPermission', () => {
    it('asks Jira for both resolved keys in the given project', async () => {
        sdkMocks.getMyPermissions.mockResolvedValue(ok(GRANTED));

        const result = await checkMeetingPermission(
            PROJECT_KEY,
            ACCOUNT_ID,
            KEYS,
        );

        expect(result).toEqual({
            hasViewMeeting: true,
            hasEditMeeting: true,
        });
        expect(sdkMocks.getMyPermissions.mock.calls[0][0]).toMatchObject({
            query: {
                projectKey: PROJECT_KEY,
                permissions: `${VIEW_KEY},${EDIT_KEY}`,
            },
        });
    });

    it('reports a permission the user does not hold as false', async () => {
        sdkMocks.getMyPermissions.mockResolvedValue(
            ok({
                permissions: {
                    [VIEW_KEY]: { havePermission: true },
                    [EDIT_KEY]: { havePermission: false },
                },
            }),
        );

        await expect(
            checkMeetingPermission(PROJECT_KEY, ACCOUNT_ID, KEYS),
        ).resolves.toEqual({ hasViewMeeting: true, hasEditMeeting: false });
    });

    it('propagates a failed check instead of denying permission', async () => {
        sdkMocks.getMyPermissions.mockResolvedValue(failed(500));

        await expect(
            checkMeetingPermission(PROJECT_KEY, ACCOUNT_ID, KEYS),
        ).rejects.toThrow(/Jira permission check failed \(status 500\)/);
    });
});

describe('ETag revalidation', () => {
    it('sends If-None-Match once Jira has offered an ETag', async () => {
        sdkMocks.getAllPermissions.mockResolvedValue(
            ok(CATALOGUE, { ETag: '"catalogue-v1"' }),
        );

        await resolveMeetingPermissionKeys();
        await resolveMeetingPermissionKeys();

        expect(headersOf(sdkMocks.getAllPermissions, 0)).toEqual({});
        expect(headersOf(sdkMocks.getAllPermissions, 1)).toEqual({
            'If-None-Match': '"catalogue-v1"',
        });
    });

    it('reuses the stored value on a bodyless 304', async () => {
        sdkMocks.getAllPermissions.mockResolvedValueOnce(
            ok(CATALOGUE, { ETag: '"catalogue-v1"' }),
        );
        await resolveMeetingPermissionKeys();

        sdkMocks.getAllPermissions.mockResolvedValueOnce(notModified());

        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);
    });

    it('keeps a 304 for the permission check from denying access', async () => {
        sdkMocks.getMyPermissions.mockResolvedValueOnce(
            ok(GRANTED, { ETag: '"mine-v1"' }),
        );
        await checkMeetingPermission(PROJECT_KEY, ACCOUNT_ID, KEYS);

        sdkMocks.getMyPermissions.mockResolvedValueOnce(notModified());

        await expect(
            checkMeetingPermission(PROJECT_KEY, ACCOUNT_ID, KEYS),
        ).resolves.toEqual({ hasViewMeeting: true, hasEditMeeting: true });
    });

    it('never replays one user validator for another', async () => {
        sdkMocks.getMyPermissions.mockResolvedValue(
            ok(GRANTED, { ETag: '"mine-v1"' }),
        );
        await checkMeetingPermission(PROJECT_KEY, ACCOUNT_ID, KEYS);

        await checkMeetingPermission(PROJECT_KEY, 'another-account', KEYS);

        expect(headersOf(sdkMocks.getMyPermissions, 1)).toEqual({});
    });

    it('scopes validators per project', async () => {
        sdkMocks.getMyPermissions.mockResolvedValue(
            ok(GRANTED, { ETag: '"mine-v1"' }),
        );
        await checkMeetingPermission(PROJECT_KEY, ACCOUNT_ID, KEYS);

        await checkMeetingPermission('OTHER', ACCOUNT_ID, KEYS);

        expect(headersOf(sdkMocks.getMyPermissions, 1)).toEqual({});
    });

    it('behaves identically when Jira offers no ETag', async () => {
        sdkMocks.getAllPermissions.mockResolvedValue(ok(CATALOGUE));

        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);
        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);

        expect(headersOf(sdkMocks.getAllPermissions, 1)).toEqual({});
        expect(localStorage.length).toBe(0);
    });

    it('adds no request of its own', async () => {
        sdkMocks.getAllPermissions.mockResolvedValue(
            ok(CATALOGUE, { ETag: '"catalogue-v1"' }),
        );

        await resolveMeetingPermissionKeys();

        expect(sdkMocks.getAllPermissions).toHaveBeenCalledTimes(1);
    });

    it('still resolves when writing the validator throws', async () => {
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('QuotaExceededError');
        });
        sdkMocks.getAllPermissions.mockResolvedValue(
            ok(CATALOGUE, { ETag: '"catalogue-v1"' }),
        );

        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);
    });

    it('still resolves when reading the validator throws', async () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('SecurityError');
        });
        sdkMocks.getAllPermissions.mockResolvedValue(
            ok(CATALOGUE, { ETag: '"catalogue-v1"' }),
        );

        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);
    });

    it('re-reads rather than trusting a corrupt validator', async () => {
        sdkMocks.getAllPermissions.mockResolvedValueOnce(
            ok(CATALOGUE, { ETag: '"catalogue-v1"' }),
        );
        await resolveMeetingPermissionKeys();

        const [key] = Object.keys(localStorage);
        localStorage.setItem(key, '{not json');
        sdkMocks.getAllPermissions.mockResolvedValueOnce(ok(CATALOGUE));

        await expect(resolveMeetingPermissionKeys()).resolves.toEqual(KEYS);
        expect(headersOf(sdkMocks.getAllPermissions, 1)).toEqual({});
    });
});
