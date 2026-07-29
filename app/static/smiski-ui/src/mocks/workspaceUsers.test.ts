import { describe, expect, it } from 'vitest';
import { MOCK_USERS } from './users';
import { searchMockWorkspaceUsers } from './workspaceUsers';

describe('searchMockWorkspaceUsers', () => {
    it('returns the full seeded directory for an empty query', async () => {
        const users = await searchMockWorkspaceUsers('');

        expect(users).toHaveLength(MOCK_USERS.length);
        for (const user of users) {
            expect(user.accountId).toBeTruthy();
            expect(user.displayName).toBeTruthy();
            expect(user.email).toBeTruthy();
        }
    });

    it('filters by a case-insensitive display-name term', async () => {
        const users = await searchMockWorkspaceUsers('alex');

        expect(users.length).toBeGreaterThan(0);
        expect(
            users.every((user) =>
                user.displayName.toLowerCase().includes('alex'),
            ),
        ).toBe(true);
    });

    it('returns an empty list when nothing matches', async () => {
        expect(await searchMockWorkspaceUsers('zzzznomatch')).toEqual([]);
    });
});
