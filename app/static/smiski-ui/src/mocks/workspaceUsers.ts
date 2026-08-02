/**
 * Mock workspace users — used only in standalone `vite dev`, where the Forge
 * bridge is not available. Mirrors the shape returned by the real
 * implementation (`api/workspaceUsers.searchWorkspaceUsers`) so
 * `useWorkspaceUsers` can swap between them by env, same pattern as
 * `mocks/issues.ts` / `mocks/projectMembers.ts`.
 *
 * Instant-create itself cannot run in `vite dev` (no backend), but the picker
 * still renders these users so the form stays inspectable.
 */
import type { WorkspaceUser } from '../api/workspaceUsers';
import { MOCK_USERS } from './users';

const NETWORK_DELAY_MS = 200;

function emailFor(accountId: string): string {
    const safe = accountId
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');
    return `${safe || 'user'}@example.invalid`;
}

const WORKSPACE_USERS: WorkspaceUser[] = MOCK_USERS.map((user) => ({
    accountId: user.accountId,
    displayName: user.displayName,
    email: emailFor(user.accountId),
    avatarUrl: user.avatarUrl,
}));

/**
 * Filter the mock directory by a case-insensitive term over the display name
 * (empty term returns the full seeded list), matching the real
 * implementation's empty-vs-query behavior.
 */
export async function searchMockWorkspaceUsers(
    query?: string,
    maxResults = 50,
): Promise<WorkspaceUser[]> {
    const term = query?.trim().toLowerCase();
    const result = WORKSPACE_USERS.filter((user) => {
        if (!term) return true;
        return user.displayName.toLowerCase().includes(term);
    }).slice(0, maxResults);

    return new Promise((resolve) =>
        setTimeout(() => resolve(result), NETWORK_DELAY_MS),
    );
}
