// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
    checkMeetingPermission: vi.fn(),
    resolveMeetingPermissionKeys: vi.fn(),
    MEETING_PERMISSION_CHECK_STALE_TIME_MS: 5 * 60 * 1000,
    MEETING_PERMISSION_KEYS_STALE_TIME_MS: Number.POSITIVE_INFINITY,
}));

vi.mock('../api/meetingPermission', () => apiMocks);
vi.mock('@forge/bridge', () => ({ requestJira: vi.fn() }));

import { CurrentUserProvider } from '../context/CurrentUserContext';
import type { ProjectMember } from '../domain';
import { queryKeys } from './queryKeys';
import { useMeetingPermissions } from './useMeetingPermission';

const KEYS = { viewKey: 'app__view-meeting', editKey: 'app__edit-meeting' };
const GRANTED = { hasViewMeeting: true, hasEditMeeting: true };

const ALICE: ProjectMember = {
    accountId: 'account-alice',
    displayName: 'Alice',
};
const BOB: ProjectMember = {
    accountId: 'account-bob',
    displayName: 'Bob',
};

function createHarness() {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    });

    const wrapperFor = (user: ProjectMember) => {
        return ({ children }: { children: ReactNode }) => (
            <QueryClientProvider client={queryClient}>
                <CurrentUserProvider user={user}>
                    {children}
                </CurrentUserProvider>
            </QueryClientProvider>
        );
    };

    return { queryClient, wrapperFor };
}

describe('useMeetingPermissions', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        apiMocks.resolveMeetingPermissionKeys.mockResolvedValue(KEYS);
        apiMocks.checkMeetingPermission.mockResolvedValue(GRANTED);
    });

    it('resolves the keys, then checks them for the user and project', async () => {
        const { wrapperFor } = createHarness();

        const { result } = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(ALICE),
        });

        await waitFor(() => expect(result.current.isLoading).toBe(false));

        expect(result.current).toMatchObject({
            canViewMeeting: true,
            canEditMeeting: true,
            error: null,
        });
        expect(apiMocks.checkMeetingPermission).toHaveBeenCalledWith(
            'SMISKI',
            ALICE.accountId,
            KEYS,
        );
    });

    it('resolves the keys once and reuses them for another project', async () => {
        const { wrapperFor } = createHarness();
        const wrapper = wrapperFor(ALICE);

        const first = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper,
        });
        await waitFor(() => expect(first.result.current.isLoading).toBe(false));

        const second = renderHook(() => useMeetingPermissions('OTHER'), {
            wrapper,
        });
        await waitFor(() =>
            expect(second.result.current.isLoading).toBe(false),
        );

        expect(apiMocks.resolveMeetingPermissionKeys).toHaveBeenCalledTimes(1);
        expect(apiMocks.checkMeetingPermission).toHaveBeenCalledTimes(2);
        expect(apiMocks.checkMeetingPermission).toHaveBeenLastCalledWith(
            'OTHER',
            ALICE.accountId,
            KEYS,
        );
    });

    it('resolves the keys once and reuses them for another user', async () => {
        const { wrapperFor } = createHarness();

        const first = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(ALICE),
        });
        await waitFor(() => expect(first.result.current.isLoading).toBe(false));

        const second = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(BOB),
        });
        await waitFor(() =>
            expect(second.result.current.isLoading).toBe(false),
        );

        expect(apiMocks.resolveMeetingPermissionKeys).toHaveBeenCalledTimes(1);
        expect(apiMocks.checkMeetingPermission).toHaveBeenLastCalledWith(
            'SMISKI',
            BOB.accountId,
            KEYS,
        );
    });

    it('does not re-check the same user and project within the TTL', async () => {
        const { wrapperFor } = createHarness();
        const wrapper = wrapperFor(ALICE);

        const first = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper,
        });
        await waitFor(() => expect(first.result.current.isLoading).toBe(false));

        const second = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper,
        });
        await waitFor(() =>
            expect(second.result.current.isLoading).toBe(false),
        );

        expect(apiMocks.checkMeetingPermission).toHaveBeenCalledTimes(1);
    });

    it('surfaces a key-resolution failure and never checks permissions', async () => {
        const { wrapperFor } = createHarness();
        apiMocks.resolveMeetingPermissionKeys.mockRejectedValue(
            new Error('permissions are not registered'),
        );

        const { result } = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(ALICE),
        });

        await waitFor(() => expect(result.current.error).not.toBeNull());

        expect(result.current.error?.message).toBe(
            'permissions are not registered',
        );
        expect(result.current.isLoading).toBe(false);
        expect(apiMocks.checkMeetingPermission).not.toHaveBeenCalled();
    });

    it('surfaces a permission-check failure rather than denying silently', async () => {
        const { wrapperFor } = createHarness();
        apiMocks.checkMeetingPermission.mockRejectedValue(
            new Error('Jira permission check failed (status 500)'),
        );

        const { result } = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(ALICE),
        });

        await waitFor(() => expect(result.current.error).not.toBeNull());

        expect(result.current.error?.message).toBe(
            'Jira permission check failed (status 500)',
        );
        expect(result.current.canViewMeeting).toBe(false);
        expect(result.current.isLoading).toBe(false);
    });

    it('reports loading while the keys are still being resolved', async () => {
        const { wrapperFor } = createHarness();
        apiMocks.resolveMeetingPermissionKeys.mockReturnValue(
            new Promise(() => {}),
        );

        const { result } = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(ALICE),
        });

        expect(result.current.isLoading).toBe(true);
        expect(result.current.canViewMeeting).toBe(false);
        expect(apiMocks.checkMeetingPermission).not.toHaveBeenCalled();
    });

    it('registers both tiers under their own cache keys', async () => {
        const { queryClient, wrapperFor } = createHarness();

        const { result } = renderHook(() => useMeetingPermissions('SMISKI'), {
            wrapper: wrapperFor(ALICE),
        });
        await waitFor(() => expect(result.current.isLoading).toBe(false));

        expect(
            queryClient.getQueryData(queryKeys.meetingPermissionKeys()),
        ).toEqual(KEYS);
        expect(
            queryClient.getQueryData(
                queryKeys.meetingPermissions('SMISKI', ALICE.accountId),
            ),
        ).toEqual(GRANTED);
    });
});
