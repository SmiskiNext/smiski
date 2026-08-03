package io.github.smiskinext.meet.application.query;

import io.github.smiskinext.shared.application.Query;

/**
 * Read-intent input for listing meetings linked to a specific Jira issue using offset pagination.
 *
 * @param issueId   the exact Jira issue id to filter on
 * @param tenantId  the resolved tenant identifier (enforced automatically by persistence)
 * @param accountId the resolved caller account identifier (auth context only, not a filter)
 * @param offset    zero-based row offset for the result page
 * @param pageSize  maximum number of items to return per page
 */
public record ListIssueMeetingsQuery(
        String issueId, String tenantId, String accountId, int offset, int pageSize)
        implements Query {}
