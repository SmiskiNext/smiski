package io.github.smiskinext.meet.application.query;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.projection.MeetingSortField;
import io.github.smiskinext.shared.application.Query;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Read-intent input for listing tenant meetings.
 *
 * <p>Carries the raw, already-defaulted request inputs plus the resolved tenant and account context.
 * Validation and page-size clamping are performed by the application service; this record only
 * transports values.
 *
 * @param tenantId   the resolved tenant identifier
 * @param accountId  the resolved caller account identifier
 * @param creatorId  host filter; {@code null} means every creator in the tenant
 * @param search     case-insensitive substring filter over title and issue key; {@code null} to skip
 * @param statuses   status filter; empty means any status
 * @param issueKey   exact issue-key filter; {@code null} to skip
 * @param projectKey exact project-key filter; {@code null} to skip
 * @param sort       the ordering mode
 * @param pageSize   the requested page size (validated and clamped by the service)
 * @param pageToken  opaque continuation cursor from a previous page; {@code null} on the first page
 */
public record ListMeetingsQuery(
        String tenantId,
        String accountId,
        @Nullable String creatorId,
        @Nullable String search,
        Set<MeetingStatus> statuses,
        @Nullable String issueKey,
        @Nullable String projectKey,
        MeetingSortField sort,
        int pageSize,
        @Nullable String pageToken)
        implements Query {

    public ListMeetingsQuery {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }
}
