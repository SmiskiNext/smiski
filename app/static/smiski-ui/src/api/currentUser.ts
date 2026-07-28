/**
 * Current Jira user API.
 *
 * The frontend-only meeting prototype still stores meeting data locally, but
 * creator/host identity must come from the Jira session rather than from the
 * mock fixture directory. `requestJira` runs as the invoking Jira user, so the
 * `/myself` response is the authoritative identity for this browser session.
 */
import { requestJira } from '@forge/bridge';
import type { ProjectMember } from '../domain';

interface JiraCurrentUserResponse {
    accountId?: string;
    displayName?: string;
    emailAddress?: string;
    avatarUrls?: Record<string, string>;
    timeZone?: string;
}

export async function getCurrentJiraUser(): Promise<ProjectMember> {
    const response = await requestJira('/rest/api/3/myself', {
        headers: { Accept: 'application/json' },
    });

    if (!response.ok) {
        throw new Error(
            `Could not load the current Jira user (Jira returned ${response.status}).`,
        );
    }

    const user = (await response.json()) as JiraCurrentUserResponse;
    if (!user.accountId || !user.displayName) {
        throw new Error('Jira returned an incomplete current-user profile.');
    }

    return {
        accountId: user.accountId,
        displayName: user.displayName,
        email: user.emailAddress,
        avatarUrl: user.avatarUrls?.['48x48'],
        timeZone: user.timeZone,
    };
}
