/**
 * Pure filter/map for Jira user search results — no `@forge/bridge` or network
 * dependency, so the workspace-user contract (only active, human Atlassian
 * accounts; mapped to `{accountId, displayName, email, avatarUrl}`) is
 * unit-testable in isolation. `workspaceUsers.ts` composes this with the Forge
 * transport.
 */
import type { User } from '@smiskinext/sdks-jira';

/** A workspace user shaped for the invite picker. */
export interface WorkspaceUser {
    accountId: string;
    displayName: string;
    email: string;
    avatarUrl?: string;
}

/**
 * Keep only active, human Atlassian accounts (excluding inactive users and
 * `app`/`customer` account types) and map them to the invite-picker shape.
 */
export function toWorkspaceUsers(users: User[]): WorkspaceUser[] {
    return users
        .filter((user) => user.active && user.accountType === 'atlassian')
        .filter((user) => Boolean(user.accountId))
        .map((user) => ({
            accountId: user.accountId as string,
            displayName: user.displayName ?? (user.accountId as string),
            email: user.emailAddress ?? '',
            avatarUrl: user.avatarUrls?.['48x48'],
        }));
}
