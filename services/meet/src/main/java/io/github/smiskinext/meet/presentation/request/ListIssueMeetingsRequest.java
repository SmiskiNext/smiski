package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.query.ListIssueMeetingsQuery;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;

/**
 * Request body for listing meetings linked to a Jira issue. Both fields are optional; an absent or
 * empty body uses defaults (offset 0, pageSize 20).
 *
 * @param offset   zero-based row offset; defaults to 0
 * @param pageSize maximum items per page; defaults to 20, range [1, 50]
 */
@Schema(description = "Pagination parameters for listing issue meetings")
public record ListIssueMeetingsRequest(
        @Schema(description = "Zero-based row offset", example = "0", nullable = true)
        @Min(0) @Nullable Integer offset,

        @Schema(description = "Page size (default 20, range 1–50)", example = "20", nullable = true)
        @Min(1) @Max(50) @Nullable Integer pageSize) {

    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public ListIssueMeetingsQuery toQuery(String issueId, String tenantId, String accountId) {
        int resolvedOffset = offset == null ? DEFAULT_OFFSET : offset;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return new ListIssueMeetingsQuery(
                issueId, tenantId, accountId, resolvedOffset, resolvedPageSize);
    }
}
