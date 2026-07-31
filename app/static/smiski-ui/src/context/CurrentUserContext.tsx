/**
 * CurrentUserContext — the invoking Jira user's identity.
 *
 * Genuinely shared across both Forge modules (meeting ownership, avatar
 * "is this me" checks, and host-conflict detection). A real Forge render reads
 * the invoking user from Jira's `/myself` endpoint. Standalone Vite development
 * keeps the deterministic mock identity because no Forge bridge exists there.
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
import { CURRENT_USER } from '../mocks/users';

const CurrentUserContext = createContext<ProjectMember>(CURRENT_USER);

export interface CurrentUserProviderProps {
    /** Optional explicit identity for tests or embedded previews. */
    user?: ProjectMember;
    children: ReactNode;
}

export function CurrentUserProvider({
    user,
    children,
}: CurrentUserProviderProps) {
    const [identityReady, setIdentityReady] = useState(
        import.meta.env.DEV || Boolean(user),
    );
    const query = useQuery({
        queryKey: queryKeys.currentUser,
        queryFn: getCurrentJiraUser,
        enabled: !import.meta.env.DEV && !user,
        staleTime: Number.POSITIVE_INFINITY,
        retry: false,
    });

    const currentUser =
        user ?? (import.meta.env.DEV ? CURRENT_USER : query.data);

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
    return useContext(CurrentUserContext);
}
