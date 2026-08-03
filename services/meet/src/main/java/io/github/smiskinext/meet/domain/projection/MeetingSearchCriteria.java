package io.github.smiskinext.meet.domain.projection;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable criteria describing a tenant meeting search.
 *
 * <p>All filters are optional and combinable. Tenant scoping is applied automatically by the
 * persistence layer and is therefore absent here. The decoded keyset {@link Position} is present
 * only on continuation requests (a page token was supplied); on the first page it is {@code null}.
 *
 * @param creatorId  filters to a single host when present; all creators when {@code null}
 * @param search     case-insensitive substring matched against title and issue key when present
 * @param statuses   restricts to the supplied statuses; all statuses when empty
 * @param issueKey   restricts to a single linked Jira issue key when present
 * @param projectKey restricts to a single linked Jira project key when present
 * @param sort       the ordering mode
 * @param position   the decoded keyset position to scroll past, or {@code null} for the first page
 */
public record MeetingSearchCriteria(
        @Nullable AccountId creatorId,
        @Nullable String search,
        Set<MeetingStatus> statuses,
        @Nullable String issueKey,
        @Nullable String projectKey,
        MeetingSortField sort,
        @Nullable Position position) {

    public MeetingSearchCriteria {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }

    /**
     * A decoded keyset cursor position.
     *
     * @param sortValue the ordering timestamp of the last row on the previous page
     * @param id        the id of the last row on the previous page
     */
    public record Position(Instant sortValue, UUID id) {}
}
