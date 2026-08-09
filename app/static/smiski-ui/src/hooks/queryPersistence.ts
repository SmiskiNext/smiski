/**
 * Persists the React Query cache to `localStorage` so it survives an iframe
 * boundary.
 *
 * This app renders many iframes that share no React tree: the Issue Panel, the
 * Project Page, and a fresh iframe for every Forge platform modal (settings,
 * schedule, instant, detail, confirm). Each one builds its own empty
 * `QueryClient`, so a `staleTime` alone — however long — buys nothing across
 * them: opening a modal re-asks Jira for data the panel behind it already has.
 * Persisting the permission reads turns those repeats into cache hits, which
 * matters because Jira meters Permissions reads in a more expensive rate-limit
 * tier than ordinary reads. Current-user identity is deliberately fetched once
 * per iframe instead of being shared through storage.
 *
 * `localStorage` is the right channel because every Forge module of this app
 * shares one origin — `utils/meetingRoomHandoff.ts` already relies on exactly
 * that to hand a meeting from the Issue Panel to the Project Page in
 * production.
 *
 * Persistence is opt-in per key, never opt-out: see
 * {@link isPersistedQueryKey}. Live meeting and room state must always come
 * from the network, so only the two permission reads are eligible.
 *
 * Nothing here may throw. A browser can refuse storage outright (private
 * browsing, disabled cookies — where even *reading* `window.localStorage`
 * raises `SecurityError`), fill its quota mid-write, or hand back JSON written
 * by an older bundle. Each of those degrades to running without a cache, in the
 * same defensive spirit as `utils/meetingRoomHandoff.ts`.
 */

import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import type { DehydrateOptions } from '@tanstack/react-query';
import type {
    AsyncStorage,
    PersistedClient,
} from '@tanstack/react-query-persist-client';
import { BUILD_VERSION } from '../utils/buildVersion';
import { isCurrentUserQueryKey, isPersistedQueryKey } from './queryKeys';

const STORAGE_KEY = 'smiski:query-cache';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

/**
 * How old a persisted cache may be and still be restored.
 *
 * This bounds the stored envelope, not the freshness of any single entry —
 * each query's own `staleTime` decides whether a restored value is served as-is
 * or revalidated in the background. A generous day therefore costs nothing in
 * correctness while covering the case worth optimising: a user who reopens the
 * issue the next morning and would otherwise pay for the permission reads
 * again.
 */
export const PERSISTED_CACHE_MAX_AGE_MS = ONE_DAY_MS;

/**
 * `gcTime` for the queries that are persisted.
 *
 * Garbage collection is what actually decides whether a restored entry can be
 * used: an entry evicted from memory is also dropped from the persisted
 * envelope on the next write, so a `gcTime` below
 * {@link PERSISTED_CACHE_MAX_AGE_MS} would quietly discard entries the
 * persister is still entitled to restore. Matching the two removes that
 * window.
 *
 * Applied per query rather than as a client-wide default on purpose. A global
 * day-long `gcTime` would also apply to `useRoomToken`, which pairs
 * `staleTime: Infinity` with a credential that expires — it would sit in memory
 * for a day and never refetch.
 */
export const PERSISTED_QUERY_GC_TIME_MS = PERSISTED_CACHE_MAX_AGE_MS;

/**
 * Restores a `localStorage`-like API that answers instead of throwing.
 *
 * Reading `window.localStorage` is itself the risky step in a browser with
 * storage disabled, so it is probed once behind a guard. When it is
 * unavailable the persister is handed `undefined`, for which its own
 * implementation substitutes no-ops — the app then runs exactly as it does
 * today, on a cold cache per iframe.
 */
function resolveStorage(): AsyncStorage<string> | undefined {
    let storage: Storage;
    try {
        if (typeof window === 'undefined' || !window.localStorage) {
            return undefined;
        }
        storage = window.localStorage;
    } catch {
        return undefined;
    }

    return {
        getItem: (key) => {
            try {
                return storage.getItem(key);
            } catch {
                return null;
            }
        },
        setItem: (key, value) => {
            try {
                storage.setItem(key, value);
            } catch {
                return;
            }
        },
        removeItem: (key) => {
            try {
                storage.removeItem(key);
            } catch {
                return;
            }
        },
    };
}

/**
 * Reads the stored envelope, treating anything unreadable as absent and
 * removing current-user queries written by older builds before hydration.
 *
 * The persist library's own restore path reacts to a deserialize failure by
 * clearing the cache and rethrowing, which surfaces a console error on every
 * mount for a condition this app is required to absorb silently. Reporting
 * corrupt JSON as an expired envelope instead takes the library's ordinary
 * "too old, discard it" branch, which clears the entry without raising.
 */
function deserializePersistedClient(cached: string): PersistedClient {
    try {
        const client = JSON.parse(cached) as PersistedClient;
        client.clientState.queries = client.clientState.queries.filter(
            (query) => !isCurrentUserQueryKey(query.queryKey),
        );
        return client;
    } catch {
        return {
            timestamp: 0,
            buster: BUILD_VERSION,
            clientState: { mutations: [], queries: [] },
        };
    }
}

/**
 * Restricts what reaches `localStorage` to the allowlisted keys, in their
 * settled state.
 *
 * `status === 'success'` repeats the library's own default, which is replaced
 * rather than extended by supplying this predicate: without it, a failed
 * permission read would be persisted and then hydrated as a failure in the next
 * iframe, turning one transient Jira error into a `NoPermissionState` that
 * outlives it.
 *
 * Mutations are excluded outright. The library persists paused mutations by
 * default so they can be resumed after a reload; here a resumed mutation would
 * be a meeting created, cancelled or ended by an iframe that no longer exists.
 */
export const dehydrateOptions: DehydrateOptions = {
    shouldDehydrateQuery: (query) =>
        query.state.status === 'success' && isPersistedQueryKey(query.queryKey),
    shouldDehydrateMutation: () => false,
};

/**
 * Options for `PersistQueryClientProvider`, which restores the cache before
 * its children mount and writes it back as it changes.
 *
 * `buster` is the build identity, so a redeployed bundle discards a cache
 * written by its predecessor rather than hydrating values whose shape it no
 * longer agrees with.
 *
 * `hydrateOptions` re-applies {@link PERSISTED_QUERY_GC_TIME_MS} to entries as
 * they are restored. A restored entry is built before anything mounts to
 * observe it, so it would otherwise take the client's default `gcTime` and
 * could be collected — and thereby dropped from storage — while still within
 * {@link PERSISTED_CACHE_MAX_AGE_MS}.
 */
export const persistOptions = {
    persister: createAsyncStoragePersister({
        storage: resolveStorage(),
        key: STORAGE_KEY,
        deserialize: deserializePersistedClient,
    }),
    maxAge: PERSISTED_CACHE_MAX_AGE_MS,
    buster: BUILD_VERSION,
    dehydrateOptions,
    hydrateOptions: {
        defaultOptions: {
            queries: { gcTime: PERSISTED_QUERY_GC_TIME_MS },
        },
    },
};
