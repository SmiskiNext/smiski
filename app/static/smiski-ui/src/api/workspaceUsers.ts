/**
 * Workspace-user search — routed through the Forge resolver.
 *
 * Unlike `projectMembers.ts` (which calls Jira directly from the browser via
 * `requestJira`), workspace-user search runs in the resolver so it can query
 * Jira as the invoking user and keep the call surface server-side. The frontend
 * only crosses the `invoke` boundary here; the resolver owns the Jira SDK.
 */
import { invoke } from '@forge/bridge';

/** A Jira site user offered as a meeting invitee. */
export interface WorkspaceUser {
    accountId: string;
    displayName: string;
    email: string;
    avatarUrl?: string;
}

/** Search Jira site users. An empty query returns an initial seeded list. */
export async function searchWorkspaceUsers(
    query?: string,
): Promise<WorkspaceUser[]> {
    return invoke('searchWorkspaceUsers', {
        query: query ?? '',
    }) as Promise<WorkspaceUser[]>;
}
