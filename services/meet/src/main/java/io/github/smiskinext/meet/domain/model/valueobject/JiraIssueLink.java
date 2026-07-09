package io.github.smiskinext.meet.domain.model.valueobject;

import io.github.smiskinext.shared.domain.ValueObject;

import java.util.Objects;

/**
 * Links a meeting to a Jira issue.
 *
 * <p>The three components map directly to the {@code meetings} table columns
 * {@code issue_id}, {@code issue_key}, and {@code project_key} (VARCHAR(64) each).
 */
public record JiraIssueLink(String issueId, String issueKey, String projectKey)
        implements ValueObject {

    public static final int MAX_LENGTH = 64;

    public JiraIssueLink {
        Objects.requireNonNull(issueId, "JiraIssueLink issueId must not be null");
        if (issueId.isBlank())
            throw new IllegalArgumentException("JiraIssueLink issueId must not be blank");
        if (issueId.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                    "JiraIssueLink issueId must not exceed " + MAX_LENGTH + " characters");

        Objects.requireNonNull(issueKey, "JiraIssueLink issueKey must not be null");
        if (issueKey.isBlank())
            throw new IllegalArgumentException("JiraIssueLink issueKey must not be blank");
        if (issueKey.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                    "JiraIssueLink issueKey must not exceed " + MAX_LENGTH + " characters");

        Objects.requireNonNull(projectKey, "JiraIssueLink projectKey must not be null");
        if (projectKey.isBlank())
            throw new IllegalArgumentException("JiraIssueLink projectKey must not be blank");
        if (projectKey.length() > MAX_LENGTH)
            throw new IllegalArgumentException(
                    "JiraIssueLink projectKey must not exceed " + MAX_LENGTH + " characters");
    }

    public static JiraIssueLink of(String issueId, String issueKey, String projectKey) {
        return new JiraIssueLink(issueId, issueKey, projectKey);
    }
}
