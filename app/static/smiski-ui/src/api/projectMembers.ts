/**
 * Project members API — real data, straight from the host Jira instance.
 *
 * Like `issues.ts` (and unlike `meetings`/`participants`/`recordings`, which
 * target the still-unbuilt backend gateway), this calls Jira ITSELF via
 * `@forge/bridge`'s `requestJira`, running as the invoking user. Needs the
 * `read:jira-user` scope (see manifest.yml) in addition to `read:jira-work`.
 */
import { requestJira } from '@forge/bridge';
import type { ProjectMember } from '../domain';

interface JiraAssignableUser {
  accountId: string;
  displayName: string;
  emailAddress?: string;
  avatarUrls?: Record<string, string>;
}

/**
 * Fetch users assignable to issues in a project — the standard Jira notion of
 * "who works on this project", used here as the meeting-participant pool.
 */
export async function getProjectMembers(
  projectKey: string,
  maxResults = 50,
): Promise<ProjectMember[]> {
  const params = new URLSearchParams({
    project: projectKey,
    maxResults: String(maxResults),
  });

  const response = await requestJira(`/rest/api/3/user/assignable/search?${params.toString()}`, {
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(
      `Could not load members for ${projectKey} (Jira returned ${response.status}).`,
    );
  }

  const users = (await response.json()) as JiraAssignableUser[];
  return users.map((user) => ({
    accountId: user.accountId,
    displayName: user.displayName,
    email: user.emailAddress,
    avatarUrl: user.avatarUrls?.['48x48'],
  }));
}
