import { describe, expect, it } from 'vitest';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import {
    mergeInviteeOptions,
    resolveSelectedInvitees,
} from './inviteeIdentity';

const alice: WorkspaceUser = {
    accountId: 'acc-alice',
    displayName: 'Alice Nguyen',
    email: 'alice@example.com',
};
const bob: WorkspaceUser = {
    accountId: 'acc-bob',
    displayName: 'Bob Tran',
    email: 'bob@example.com',
};

describe('WorkspaceUserPicker identity retention', () => {
    it('keeps a selected invitee in the options across a non-matching search', () => {
        // Search returned Bob only, but Alice is already selected.
        const options = mergeInviteeOptions([alice], [bob]);

        expect(options.map((user) => user.accountId)).toContain('acc-alice');
        expect(options.map((user) => user.accountId)).toContain('acc-bob');
        const selectedAlice = options.find(
            (user) => user.accountId === 'acc-alice',
        );
        expect(selectedAlice?.displayName).toBe('Alice Nguyen');
    });

    it('does not duplicate a user present in both selection and results', () => {
        const options = mergeInviteeOptions([alice], [alice, bob]);
        expect(options).toHaveLength(2);
    });

    it('resolves selected ids to full invitees, preserving prior selection when results are empty (error/empty state)', () => {
        // A failed/empty search means fetched=[]; the prior selection is kept.
        const resolved = resolveSelectedInvitees(['acc-alice'], [alice], []);
        expect(resolved).toEqual([alice]);
    });

    it('falls back to a minimal record rather than dropping an unknown id', () => {
        const resolved = resolveSelectedInvitees(['acc-ghost'], [], []);
        expect(resolved).toEqual([
            { accountId: 'acc-ghost', displayName: 'acc-ghost', email: '' },
        ]);
    });
});
