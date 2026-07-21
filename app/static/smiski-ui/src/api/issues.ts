/**
 * Jira issues API — real data, straight from the host Jira instance.
 *
 * Unlike `meetings`/`participants`/`recordings` (which target the still-unbuilt
 * Kong Gateway backend), issues come from Jira ITSELF via `@forge/bridge`'s
 * `requestJira`. That call is a first-class Forge capability — it runs as the
 * invoking user and only needs the `read:jira-work` scope (already granted in
 * manifest.yml), so this works today with no backend.
 */
import { requestJira } from '@forge/bridge';
import type { JiraIssue } from '../domain';

interface JiraSearchResponse {
  issues?: Array<{ id: string; key: string; fields?: { summary?: string } }>;
}

/** Escape double quotes so a summary term can't break out of the JQL string. */
function escapeJql(value: string): string {
  return value.replace(/["\\]/g, '\\$&');
}

/**
 * Fetch issues in a project, newest-first, optionally narrowed by a free-text
 * term matched against the summary. Returns at most `maxResults` issues.
 */
export async function getProjectIssues(
  projectKey: string,
  query?: string,
  maxResults = 50,
): Promise<JiraIssue[]> {
  const term = query?.trim();
  const jql =
    `project = "${escapeJql(projectKey)}"` +
    (term ? ` AND summary ~ "${escapeJql(term)}*"` : '') +
    ' ORDER BY updated DESC';

  const params = new URLSearchParams({
    jql,
    maxResults: String(maxResults),
    fields: 'summary',
  });

  const response = await requestJira(`/rest/api/3/search/jql?${params.toString()}`, {
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`Could not load issues for ${projectKey} (Jira returned ${response.status}).`);
  }

  const data = (await response.json()) as JiraSearchResponse;
  return (data.issues ?? []).map((issue) => ({
    id: issue.id,
    key: issue.key,
    summary: issue.fields?.summary ?? '',
  }));
}
