// @vitest-environment jsdom

import { dehydrate, QueryClient } from '@tanstack/react-query';
import { persistQueryClientRestore } from '@tanstack/react-query-persist-client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@forge/bridge', () => ({ requestJira: vi.fn() }));

import { queryKeys } from './queryKeys';
import {
    dehydrateOptions,
    PERSISTED_CACHE_MAX_AGE_MS,
    PERSISTED_QUERY_GC_TIME_MS,
    persistOptions,
} from './queryPersistence';

const ALICE = { accountId: 'account-alice', displayName: 'Alice' };
const MEETING_ID = '0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90';

function dehydratedKeys(client: QueryClient): unknown[][] {
    return dehydrate(client, dehydrateOptions).queries.map(
        (query) => query.queryKey as unknown[],
    );
}

describe('persisted query allowlist', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('persists the three slow-moving reads', () => {
        const client = new QueryClient();
        client.setQueryData(queryKeys.currentUser, ALICE);
        client.setQueryData(queryKeys.meetingPermissionKeys(), {
            viewKey: 'v',
            editKey: 'e',
        });
        client.setQueryData(
            queryKeys.meetingPermissions('SMISKI', ALICE.accountId),
            { hasViewMeeting: true, hasEditMeeting: false },
        );

        expect(dehydratedKeys(client)).toHaveLength(3);
    });

    it('persists a permission check for any project and any user', () => {
        const client = new QueryClient();
        client.setQueryData(queryKeys.meetingPermissions('SMISKI', 'a'), {});
        client.setQueryData(queryKeys.meetingPermissions('OTHER', 'b'), {});

        expect(dehydratedKeys(client)).toHaveLength(2);
    });

    it('persists no live meeting or room state', () => {
        const client = new QueryClient();
        client.setQueryData(queryKeys.issueMeetings('SMISKI-1'), []);
        client.setQueryData(queryKeys.projectMeetings({}), []);
        client.setQueryData(queryKeys.projectIssues('SMISKI'), []);
        client.setQueryData(queryKeys.projectMembers('SMISKI'), []);
        client.setQueryData(queryKeys.workspaceUsers('al'), []);
        client.setQueryData(queryKeys.meeting(MEETING_ID), {});
        client.setQueryData(queryKeys.pendingJoinRequests(MEETING_ID), []);
        client.setQueryData(
            queryKeys.pendingJoinRequestsPage(MEETING_ID, {}),
            {},
        );
        client.setQueryData(queryKeys.hostConflict(ALICE.accountId), null);
        client.setQueryData(queryKeys.roomToken(MEETING_ID), 'jwt');

        expect(dehydratedKeys(client)).toEqual([]);
    });

    it('does not persist a key that merely resembles an allowlisted root', () => {
        const client = new QueryClient();
        client.setQueryData(['meeting-permissions-draft', 'SMISKI'], {});
        client.setQueryData(['jira', 'current-users'], ALICE);

        expect(dehydratedKeys(client)).toEqual([]);
    });

    it('matches by prefix, as TanStack filters do', () => {
        const client = new QueryClient();
        client.setQueryData([...queryKeys.currentUser, 'detail'], ALICE);

        expect(dehydratedKeys(client)).toEqual([
            ['jira', 'current-user', 'detail'],
        ]);
    });

    it('does not persist a failed permission read as a settled denial', () => {
        const client = new QueryClient();
        const queryKey = queryKeys.meetingPermissions('SMISKI', 'a');
        client
            .getQueryCache()
            .build(client, { queryKey })
            .setState({
                status: 'error',
                error: new Error('Jira permission check failed'),
            });

        expect(dehydratedKeys(client)).toEqual([]);
    });

    it('never persists a mutation', () => {
        const client = new QueryClient();
        client.getMutationCache().build(
            client,
            { mutationKey: ['cancel-meeting'] },
            {
                status: 'pending',
                isPaused: true,
                variables: { meetingId: MEETING_ID },
                context: undefined,
                data: undefined,
                error: null,
                failureCount: 0,
                failureReason: null,
                submittedAt: Date.now(),
            },
        );

        expect(dehydrate(client, dehydrateOptions).mutations).toEqual([]);
    });
});

describe('persister resilience', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('keeps gcTime at least as long as the persisted envelope may live', () => {
        expect(PERSISTED_QUERY_GC_TIME_MS).toBeGreaterThanOrEqual(
            PERSISTED_CACHE_MAX_AGE_MS,
        );
    });

    it('restores a value written by the same build', async () => {
        const source = new QueryClient();
        source.setQueryData(queryKeys.currentUser, ALICE);
        await persistOptions.persister.persistClient({
            timestamp: Date.now(),
            buster: persistOptions.buster,
            clientState: dehydrate(source, dehydrateOptions),
        });

        const target = new QueryClient();
        await persistQueryClientRestore({
            queryClient: target,
            ...persistOptions,
        });

        expect(target.getQueryData(queryKeys.currentUser)).toEqual(ALICE);
    });

    it('discards a cache written by a different build', async () => {
        const source = new QueryClient();
        source.setQueryData(queryKeys.currentUser, ALICE);
        await persistOptions.persister.persistClient({
            timestamp: Date.now(),
            buster: 'a-previous-deploy',
            clientState: dehydrate(source, dehydrateOptions),
        });

        const target = new QueryClient();
        await persistQueryClientRestore({
            queryClient: target,
            ...persistOptions,
        });

        expect(target.getQueryData(queryKeys.currentUser)).toBeUndefined();
    });

    it('discards an envelope older than the max age', async () => {
        const source = new QueryClient();
        source.setQueryData(queryKeys.currentUser, ALICE);
        await persistOptions.persister.persistClient({
            timestamp: Date.now() - PERSISTED_CACHE_MAX_AGE_MS - 1,
            buster: persistOptions.buster,
            clientState: dehydrate(source, dehydrateOptions),
        });

        const target = new QueryClient();
        await persistQueryClientRestore({
            queryClient: target,
            ...persistOptions,
        });

        expect(target.getQueryData(queryKeys.currentUser)).toBeUndefined();
    });

    it('restores a hydrated entry with a gcTime that outlives the envelope', async () => {
        const source = new QueryClient();
        source.setQueryData(queryKeys.currentUser, ALICE);
        await persistOptions.persister.persistClient({
            timestamp: Date.now(),
            buster: persistOptions.buster,
            clientState: dehydrate(source, dehydrateOptions),
        });

        const target = new QueryClient();
        await persistQueryClientRestore({
            queryClient: target,
            ...persistOptions,
        });

        const restored = target
            .getQueryCache()
            .find({ queryKey: queryKeys.currentUser });
        expect(restored?.gcTime).toBeGreaterThanOrEqual(
            PERSISTED_CACHE_MAX_AGE_MS,
        );
    });

    it('treats corrupt JSON as an absent cache instead of throwing', async () => {
        localStorage.setItem('smiski:query-cache', '{not json');

        const target = new QueryClient();
        await expect(
            persistQueryClientRestore({
                queryClient: target,
                ...persistOptions,
            }),
        ).resolves.toBeUndefined();
        expect(target.getQueryData(queryKeys.currentUser)).toBeUndefined();
    });

    it('degrades silently when the quota is exceeded mid-write', async () => {
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('QuotaExceededError');
        });
        const source = new QueryClient();
        source.setQueryData(queryKeys.currentUser, ALICE);

        await expect(
            persistOptions.persister.persistClient({
                timestamp: Date.now(),
                buster: persistOptions.buster,
                clientState: dehydrate(source, dehydrateOptions),
            }),
        ).resolves.toBeUndefined();
    });

    it('degrades silently when reading storage throws', async () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('SecurityError');
        });

        const target = new QueryClient();
        await expect(
            persistQueryClientRestore({
                queryClient: target,
                ...persistOptions,
            }),
        ).resolves.toBeUndefined();
    });

    it('runs without a cache when the browser refuses storage outright', async () => {
        vi.resetModules();
        vi.stubGlobal('window', {
            get localStorage(): Storage {
                throw new Error('SecurityError');
            },
        });

        const isolated = await import('./queryPersistence');

        await expect(
            isolated.persistOptions.persister.restoreClient(),
        ).resolves.toBeUndefined();

        vi.unstubAllGlobals();
        vi.resetModules();
    });
});
