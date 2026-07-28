/**
 * Pure identity-retention helpers for `WorkspaceUserPicker`.
 *
 * The invite picker searches Jira site users server-side, so the current search
 * results won't always contain the invitees a host already selected. These
 * helpers merge the selected invitees with the latest fetched results (keyed by
 * `accountId`) so a chosen invitee keeps its full identity — and stays
 * displayed — even when it is not part of the current results. Kept free of
 * React/Ant Design imports so the retention logic is unit-testable in isolation.
 */
import type { WorkspaceUser } from '../../api/workspaceUsers';

function indexByAccountId(
    selected: WorkspaceUser[],
    fetched: WorkspaceUser[],
): Map<string, WorkspaceUser> {
    const byId = new Map<string, WorkspaceUser>();
    for (const user of selected) byId.set(user.accountId, user);
    for (const user of fetched) byId.set(user.accountId, user);
    return byId;
}

/**
 * The option list to display: the union of already-selected invitees and the
 * latest fetched results, de-duplicated by `accountId`.
 */
export function mergeInviteeOptions(
    selected: WorkspaceUser[],
    fetched: WorkspaceUser[],
): WorkspaceUser[] {
    return Array.from(indexByAccountId(selected, fetched).values());
}

/**
 * Resolve the selected `accountId`s back to full invitees, preferring known
 * identities (selected or fetched) and falling back to a minimal record so a
 * selection is never silently dropped.
 */
export function resolveSelectedInvitees(
    ids: string[],
    selected: WorkspaceUser[],
    fetched: WorkspaceUser[],
): WorkspaceUser[] {
    const byId = indexByAccountId(selected, fetched);
    return ids.map(
        (id) =>
            byId.get(id) ?? {
                accountId: id,
                displayName: id,
                email: '',
            },
    );
}
