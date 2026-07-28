import { describe, expect, it } from 'vitest';
/**
 * Tests the resolver-side pure filter/map (`app/src/workspaceUserMapping.ts`).
 * That module lives in the Forge resolver package but has no `@forge/api` or
 * network dependency, so it is imported here to unit-test the workspace-user
 * exclusion + field-mapping contract in the existing smiski-ui vitest runner.
 */
import { toWorkspaceUsers } from '../../../../src/workspaceUserMapping';

describe('resolver searchUsers mapping/filter (toWorkspaceUsers)', () => {
    it('excludes inactive and non-human accounts, keeping active atlassian users', () => {
        const result = toWorkspaceUsers([
            {
                accountId: 'acc-active',
                displayName: 'Active Human',
                emailAddress: 'active@example.com',
                accountType: 'atlassian',
                active: true,
                avatarUrls: { '48x48': 'https://avatar/48' },
            },
            {
                accountId: 'acc-inactive',
                displayName: 'Inactive Human',
                accountType: 'atlassian',
                active: false,
            },
            {
                accountId: 'acc-app',
                displayName: 'App Account',
                accountType: 'app',
                active: true,
            },
            {
                accountId: 'acc-customer',
                displayName: 'Customer Account',
                accountType: 'customer',
                active: true,
            },
        ]);

        expect(result).toHaveLength(1);
        expect(result[0]).toEqual({
            accountId: 'acc-active',
            displayName: 'Active Human',
            email: 'active@example.com',
            avatarUrl: 'https://avatar/48',
        });
    });

    it('maps the 48x48 avatar and tolerates a missing email', () => {
        const result = toWorkspaceUsers([
            {
                accountId: 'acc-1',
                displayName: 'No Email',
                accountType: 'atlassian',
                active: true,
                avatarUrls: {
                    '16x16': 'https://avatar/16',
                    '48x48': 'https://avatar/48',
                },
            },
        ]);

        expect(result[0]).toMatchObject({
            accountId: 'acc-1',
            displayName: 'No Email',
            email: '',
            avatarUrl: 'https://avatar/48',
        });
    });

    it('drops entries without an accountId', () => {
        const result = toWorkspaceUsers([
            {
                displayName: 'Anonymous',
                accountType: 'atlassian',
                active: true,
            },
        ]);

        expect(result).toEqual([]);
    });
});
