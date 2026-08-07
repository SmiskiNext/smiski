// @vitest-environment jsdom

import { QueryClient } from '@tanstack/react-query';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const requestJiraMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    requestJira: (...args: unknown[]) => requestJiraMock(...args),
}));

import { CurrentUserProvider } from '../context/CurrentUserContext';
import { queryKeys } from './queryKeys';
import { persistOptions } from './queryPersistence';

const STORAGE_KEY = 'smiski:query-cache';

const ALICE = {
    accountId: 'account-alice',
    displayName: 'Alice',
};

/**
 * Writes an envelope the way a previous iframe would have left it behind.
 *
 * Written straight to storage rather than through `persistOptions.persister`,
 * whose `persistClient` is throttled: a save still queued from an earlier test's
 * provider subscription makes a seeding call return without writing anything.
 */
function seedPersistedIdentity(dataUpdatedAt: number): void {
    localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
            timestamp: Date.now(),
            buster: persistOptions.buster,
            clientState: {
                mutations: [],
                queries: [
                    {
                        queryKey: queryKeys.currentUser,
                        queryHash: JSON.stringify(queryKeys.currentUser),
                        state: {
                            data: ALICE,
                            dataUpdateCount: 1,
                            dataUpdatedAt,
                            error: null,
                            errorUpdateCount: 0,
                            errorUpdatedAt: 0,
                            fetchFailureCount: 0,
                            fetchFailureReason: null,
                            fetchMeta: null,
                            isInvalidated: false,
                            status: 'success',
                            fetchStatus: 'idle',
                        },
                    },
                ],
            },
        }),
    );
}

function renderThroughPersistGate(): void {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    });

    render(
        <PersistQueryClientProvider
            client={queryClient}
            persistOptions={persistOptions}
        >
            <CurrentUserProvider>
                <span>surface mounted</span>
            </CurrentUserProvider>
        </PersistQueryClientProvider>,
    );
}

describe('restoring through PersistQueryClientProvider', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        vi.restoreAllMocks();
        localStorage.clear();
        requestJiraMock.mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({
                accountId: ALICE.accountId,
                displayName: ALICE.displayName,
            }),
        });
    });

    afterEach(() => {
        cleanup();
    });

    it('renders on a cold cache by fetching once the restore gate opens', async () => {
        renderThroughPersistGate();

        expect(await screen.findByText('surface mounted')).toBeDefined();
        expect(requestJiraMock).toHaveBeenCalledTimes(1);
    });

    it('renders on a corrupt cache instead of failing closed', async () => {
        localStorage.setItem(STORAGE_KEY, '{not json');

        renderThroughPersistGate();

        expect(await screen.findByText('surface mounted')).toBeDefined();
        expect(requestJiraMock).toHaveBeenCalledTimes(1);
    });

    it('renders a restored identity without asking Jira again', async () => {
        seedPersistedIdentity(Date.now());

        renderThroughPersistGate();

        expect(await screen.findByText('surface mounted')).toBeDefined();
        expect(requestJiraMock).not.toHaveBeenCalled();
    });

    it('discards a restored identity once the envelope is too old', async () => {
        seedPersistedIdentity(Date.now());
        const envelope = JSON.parse(
            localStorage.getItem(STORAGE_KEY) as string,
        );
        envelope.timestamp = Date.now() - persistOptions.maxAge - 1;
        localStorage.setItem(STORAGE_KEY, JSON.stringify(envelope));

        renderThroughPersistGate();

        expect(await screen.findByText('surface mounted')).toBeDefined();
        await waitFor(() => expect(requestJiraMock).toHaveBeenCalledTimes(1));
    });
});
