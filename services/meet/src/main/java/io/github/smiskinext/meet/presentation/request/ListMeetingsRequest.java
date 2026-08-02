package io.github.smiskinext.meet.presentation.request;

import io.github.smiskinext.meet.application.query.ListMeetingsQuery;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Request body for listing tenant meetings. Every field is optional; an absent or empty body lists
 * the tenant's meetings with defaults.
 *
 * @param creatorId  filters to a single host; omit for every creator in the tenant
 * @param search     case-insensitive substring over title and issue key
 * @param statuses   restricts to the supplied statuses; omit or empty for any status
 * @param issueKey   exact linked Jira issue key filter
 * @param projectKey exact linked Jira project key filter
 * @param sort       ordering mode; defaults to {@code CREATED_AT}
 * @param pageSize   page size; defaults to 20, rejected when greater than 50
 * @param pageToken  opaque continuation cursor from a previous response
 */
@Schema(description = "Filters and pagination for listing tenant meetings")
public record ListMeetingsRequest(
        @Schema(description = "Filter to a single host account id", nullable = true) @Nullable String creatorId,

        @Schema(
                description = "Case-insensitive substring over title and issue key",
                nullable = true)
        @Nullable String search,

        @Schema(description = "Restrict to these statuses; empty means any", nullable = true)
        @Nullable List<MeetingStatus> statuses,

        @Schema(
                description = "Exact linked Jira issue key",
                example = "SMISKI-102",
                nullable = true)
        @Nullable String issueKey,

        @Schema(description = "Exact linked Jira project key", example = "SMISKI", nullable = true)
        @Nullable String projectKey,

        @Schema(description = "Ordering mode", nullable = true) @Nullable MeetingSortField sort,

        @Schema(description = "Page size (default 20, max 50)", example = "20", nullable = true)
        @Min(1) @Max(50) @Nullable Integer pageSize,

        @Schema(description = "Opaque cursor from a previous response", nullable = true) @Nullable String pageToken) {

    private static final int DEFAULT_PAGE_SIZE = 20;

    public ListMeetingsQuery toQuery(String tenantId, String accountId) {
        Set<MeetingStatus> statusSet = statuses == null ? Set.of() : Set.copyOf(statuses);
        MeetingSortField resolvedSort = sort == null ? MeetingSortField.CREATED_AT : sort;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;

        return new ListMeetingsQuery(
                tenantId,
                accountId,
                creatorId,
                search,
                statusSet,
                issueKey,
                projectKey,
                resolvedSort,
                resolvedPageSize,
                pageToken);
    }
}
