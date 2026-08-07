/**
 * CurrentUserContext — the invoking Jira user's identity.
 *
 * Genuinely shared across both Forge modules (meeting ownership, avatar
 * "is this me" checks, and host-conflict detection). The invoking user is read
 * from Jira's `/myself` endpoint, so a Forge context is required. The provider
 * renders children only once an identity is resolved, which is what lets
 * `useCurrentUser` expose a non-optional `ProjectMember`.
 *
 * The identity is one of the few reads persisted across iframes
 * (`hooks/queryPersistence.ts`): every platform modal opens in its own iframe
 * and would otherwise re-ask `/myself` for an answer that cannot have changed
 * mid-session. `gcTime` matches the persister's `maxAge` so the entry is not
 * collected — and thereby dropped from storage — while still restorable.
 */

import { useQuery } from '@tanstack/react-query';
import {
    createContext,
    type ReactNode,
    useContext,
    useEffect,
    useState,
} from 'react';
import { getCurrentJiraUser } from '../api/currentUser';
import type { ProjectMember } from '../domain';
import { queryKeys } from '../hooks/queryKeys';
import { PERSISTED_QUERY_GC_TIME_MS } from '../hooks/queryPersistence';

const CurrentUserContext = createContext<ProjectMember | undefined>(undefined);

export interface CurrentUserProviderProps {
    /** Optional explicit identity for tests or embedded previews. */
    user?: ProjectMember;
    children: ReactNode;
}

export function CurrentUserProvider({
    user,
    children,
}: CurrentUserProviderProps) {
    const [identityReady, setIdentityReady] = useState(Boolean(user));
    const query = useQuery({
        queryKey: queryKeys.currentUser,
        queryFn: getCurrentJiraUser,
        enabled: !user,
        staleTime: Number.POSITIVE_INFINITY,
        gcTime: PERSISTED_QUERY_GC_TIME_MS,
        retry: false,
    });

    const currentUser = user ?? query.data;

    useEffect(() => {
        if (!currentUser) return;
        setIdentityReady(true);
    }, [currentUser]);

    if (!currentUser || !identityReady) {
        if (query.error) {
            return (
                <div className='m-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300'>
                    {query.error.message} Ensure the app is upgraded with the
                    `read:jira-user` scope.
                </div>
            );
        }

        return (
            <div className='p-6 text-sm text-[var(--text-muted)]'>
                Loading your Jira profile…
            </div>
        );
    }

    return (
        <CurrentUserContext.Provider value={currentUser}>
            {children}
        </CurrentUserContext.Provider>
    );
}

// Co-locating this tiny hook with its provider keeps the context boundary discoverable.
// eslint-disable-next-line react-refresh/only-export-components
export function useCurrentUser(): ProjectMember {
    const currentUser = useContext(CurrentUserContext);

    if (!currentUser) {
        throw new Error(
            'useCurrentUser must be used within a CurrentUserProvider.',
        );
    }

    return currentUser;
}
