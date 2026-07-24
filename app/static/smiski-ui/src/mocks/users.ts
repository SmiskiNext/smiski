/**
 * Mock Jira user directory — stands in for the backend user-management
 * service's gRPC name/avatar resolution during frontend-only development.
 */

export interface MockUser {
    accountId: string;
    displayName: string;
    avatarUrl?: string;
}

const AVATAR_PALETTE = [
    '#0052CC',
    '#00875A',
    '#FF8B00',
    '#6554C0',
    '#DE350B',
    '#00A3BF',
];

function initialsOf(name: string): string {
    const parts = name.trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
}

/**
 * Self-contained SVG data URI avatar (initials on a flat color background).
 * No network egress — avoids Forge CSP / external-fetch configuration entirely.
 */
function initialsAvatar(name: string, colorIndex: number): string {
    const color = AVATAR_PALETTE[colorIndex % AVATAR_PALETTE.length];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64">
    <rect width="64" height="64" rx="32" fill="${color}" />
    <text x="32" y="32" dy="0.35em" text-anchor="middle" font-family="Arial, sans-serif" font-size="24" fill="#FFFFFF">${initialsOf(name)}</text>
  </svg>`;
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

export const CURRENT_USER: MockUser = {
    accountId: 'acc-current-jordan',
    displayName: 'Jordan Avery',
    avatarUrl: initialsAvatar('Jordan Avery', 0),
};

export const MOCK_USERS: MockUser[] = [
    CURRENT_USER,
    {
        accountId: 'acc-alex-chen',
        displayName: 'Alex Chen',
        avatarUrl: initialsAvatar('Alex Chen', 1),
    },
    {
        accountId: 'acc-bao-tran',
        displayName: 'Bao Tran',
        avatarUrl: initialsAvatar('Bao Tran', 2),
    },
    {
        accountId: 'acc-chi-nguyen',
        displayName: 'Chi Nguyen',
        avatarUrl: initialsAvatar('Chi Nguyen', 3),
    },
    {
        accountId: 'acc-minh-le',
        displayName: 'Minh Le',
        avatarUrl: initialsAvatar('Minh Le', 4),
    },
    {
        accountId: 'acc-sara-kim',
        displayName: 'Sara Kim',
        avatarUrl: initialsAvatar('Sara Kim', 5),
    },
    {
        accountId: 'acc-diego-alvarez',
        displayName: 'Diego Alvarez',
        avatarUrl: initialsAvatar('Diego Alvarez', 6),
    },
];

export function findMockUser(accountId: string): MockUser | undefined {
    return MOCK_USERS.find((u) => u.accountId === accountId);
}
