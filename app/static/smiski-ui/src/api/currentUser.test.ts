import { beforeEach, describe, expect, it, vi } from 'vitest';

const requestJiraMock = vi.fn();
vi.mock('@forge/bridge', () => ({
    requestJira: (...args: unknown[]) => requestJiraMock(...args),
}));

import { getCurrentJiraUser } from './currentUser';

function jiraResponse(ok: boolean, status: number, body: unknown) {
    return { ok, status, json: async () => body };
}

describe('getCurrentJiraUser', () => {
    beforeEach(() => {
        requestJiraMock.mockReset();
    });

    it('maps the /myself timeZone into ProjectMember.timeZone', async () => {
        requestJiraMock.mockResolvedValue(
            jiraResponse(true, 200, {
                accountId: 'acc-123',
                displayName: 'Alice Nguyen',
                emailAddress: 'alice@example.com',
                avatarUrls: { '48x48': 'https://avatar.example/alice.png' },
                timeZone: 'Asia/Ho_Chi_Minh',
            }),
        );

        const user = await getCurrentJiraUser();

        expect(user).toMatchObject({
            accountId: 'acc-123',
            displayName: 'Alice Nguyen',
            email: 'alice@example.com',
            timeZone: 'Asia/Ho_Chi_Minh',
        });
    });

    it('leaves timeZone undefined when /myself omits it', async () => {
        requestJiraMock.mockResolvedValue(
            jiraResponse(true, 200, {
                accountId: 'acc-123',
                displayName: 'Alice Nguyen',
            }),
        );

        const user = await getCurrentJiraUser();

        expect(user.timeZone).toBeUndefined();
    });
});
