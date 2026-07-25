/**
 * Mock Jira issues — used only in standalone `vite dev`, where the real
 * `requestJira` bridge is not available. Mirrors the shape returned by
 * `api/issues.getProjectIssues` so the hook can swap between them by env.
 */
import type { JiraIssue } from '../domain';

const MOCK_ISSUES: JiraIssue[] = [
    {
        id: 'issue-SMISKI-101',
        key: 'SMISKI-101',
        summary: 'Quản lý người tham gia',
    },
    {
        id: 'issue-SMISKI-102',
        key: 'SMISKI-102',
        summary: 'Quản lý vòng đời cuộc họp',
    },
    {
        id: 'issue-SMISKI-103',
        key: 'SMISKI-103',
        summary: 'Tạo cuộc họp tức thì',
    },
    { id: 'issue-SMISKI-104', key: 'SMISKI-104', summary: 'Lên lịch cuộc họp' },
    {
        id: 'issue-SMISKI-55',
        key: 'SMISKI-55',
        summary: 'Incident bridge — payments outage',
    },
    {
        id: 'issue-SMISKI-201',
        key: 'SMISKI-201',
        summary: 'Hiển thị Meeting trong Jira Issue Panel',
    },
    {
        id: 'issue-SMISKI-202',
        key: 'SMISKI-202',
        summary: 'Quản lý Meeting tại Project page',
    },
    {
        id: 'issue-SMISKI-203',
        key: 'SMISKI-203',
        summary: 'Đồng bộ dữ liệu với Jira',
    },
    {
        id: 'issue-SMISKI-204',
        key: 'SMISKI-204',
        summary: 'Triển khai LiveKit Server',
    },
    {
        id: 'issue-SMISKI-205',
        key: 'SMISKI-205',
        summary: 'Thiết lập PostgreSQL và Redis',
    },
];

const NETWORK_DELAY_MS = 250;

export async function listProjectIssues(
    projectKey: string,
    query?: string,
    maxResults = 50,
): Promise<JiraIssue[]> {
    const term = query?.trim().toLowerCase();
    const result = MOCK_ISSUES.filter((issue) => {
        if (!issue.key.startsWith(`${projectKey}-`)) return false;
        if (!term) return true;
        return (
            issue.summary.toLowerCase().includes(term) ||
            issue.key.toLowerCase().includes(term)
        );
    }).slice(0, maxResults);

    return new Promise((resolve) =>
        setTimeout(() => resolve(result), NETWORK_DELAY_MS),
    );
}
