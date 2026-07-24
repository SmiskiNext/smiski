/**
 * A Jira issue, as needed to bind a meeting to it. This is intentionally a
 * thin slice of Jira's full issue model — just what the meeting UI shows and
 * stores (the key the user sees, the id the backend keys on, and a summary).
 */
export interface JiraIssue {
    /** Numeric Jira issue id (stable across renames). */
    id: string;
    /** Human-facing key, e.g. `SMISKI-123`. */
    key: string;
    /** Issue summary/title. */
    summary: string;
}
