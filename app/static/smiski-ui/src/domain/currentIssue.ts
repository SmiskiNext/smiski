/**
 * The Jira Issue (+ its Project) the `jira:issueContext` module is currently
 * mounted in. Distinct from `JiraIssue` (issue.ts), which shapes a *picked*
 * issue from search — this is the panel's own scope, always fully known
 * (never optional/loading) once App.tsx resolves the Forge module context.
 */
export interface CurrentIssueContextValue {
  issueKey: string;
  issueId: string;
  projectKey: string;
}
