package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.projection.MeetingDetail;
import io.github.smiskinext.meet.domain.projection.MeetingSearchCriteria;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.meet.domain.projection.ParticipatedMeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.OffsetPageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface MeetingRepository {

    /**
     * Persists the meeting, flushing immediately so a short-code uniqueness violation surfaces at
     * the call site.
     *
     * @throws io.github.smiskinext.meet.domain.model.valueobject.ShortCodeCollisionException when
     *     the meeting's short code is already used by another live meeting in the same tenant
     */
    Meeting save(Meeting meeting);

    Optional<Meeting> findById(UUID id);

    /**
     * Fetches a read-only detail projection for a single meeting via constructor projection,
     * avoiding full aggregate reconstitution.
     *
     * <p>Returns an empty result for both unknown and soft-deleted meetings, so callers cannot
     * distinguish the two. Tenant isolation is enforced by the entity's {@code @TenantId} filter.
     */
    Optional<MeetingDetail> findDetailById(UUID id);

    /**
     * Finds a meeting by ID with a pessimistic write lock (SELECT FOR UPDATE).
     */
    Optional<Meeting> findByIdWithLock(UUID id);

    /**
     * Finds an active (not soft-deleted) meeting by ID without locking. Soft-deleted and unknown
     * meetings both return an empty result, so callers cannot distinguish them.
     */
    Optional<Meeting> findActiveById(UUID id);

    /**
     * Finds an active (not soft-deleted) meeting by ID with a pessimistic write lock. Soft-deleted
     * and unknown meetings both return an empty result, so callers cannot distinguish them.
     */
    Optional<Meeting> findActiveByIdWithLock(UUID id);

    Optional<Meeting> findByShortCode(ShortCode shortCode);

    /**
     * Finds a meeting by its calendar UID (used for inbound iMIP reply resolution).
     * The lookup spans all tenants because the UID from an iMIP reply carries no tenant context.
     */
    Optional<Meeting> findByCalendarUid(String calendarUid);

    /**
     * Lists tenant meetings matching the given criteria using keyset pagination.
     *
     * <p>Fetches at most {@code pageSize} items; the returned {@link CursorPageResponse#hasNext()}
     * reflects whether further pages exist, detected without a total count.
     *
     * @param criteria the combined filters, sort mode, and decoded keyset position
     * @param pageSize  the maximum number of items to return
     * @return a page of meeting summaries ordered per the criteria's sort mode
     */
    CursorPageResponse<MeetingSummary> searchSummaries(
            MeetingSearchCriteria criteria, int pageSize);

    CursorPageResponse<ParticipatedMeetingSummary> findParticipatedSummariesByAccountId(
            AccountId accountId,
            Set<MeetingStatus> statuses,
            @Nullable ParticipatedMeetingCursor cursor,
            int pageSize);

    /**
     * Returns an offset-paginated page of meeting summaries linked to the given issue.
     *
     * <p>Tenant scoping is applied automatically by the persistence layer. Soft-deleted meetings
     * are excluded from both the returned items and the total count.
     *
     * @param issueId  the exact Jira issue id to filter on
     * @param offset   zero-based row offset (not a page number)
     * @param pageSize maximum items to return
     * @return an offset page carrying items, total count, and a hasNext flag
     */
    IssueMeetingPage findSummariesByIssueId(String issueId, int offset, int pageSize);

    /**
     * Offset page of meeting summaries for a single issue, including the total count.
     *
     * @param page  the offset-paginated slice of summaries
     * @param total the total number of non-deleted meetings linked to the issue in this tenant
     */
    record IssueMeetingPage(OffsetPageResponse<MeetingSummary> page, long total) {}

    /**
     * Finds SCHEDULED meetings whose end time is before {@code cutoff}, across all tenants,
     * bypassing the tenant context. Uses {@code FOR UPDATE SKIP LOCKED} to prevent double
     * processing by concurrent job instances.
     *
     * @param batchSize maximum number of results to return
     * @param cutoff    only meetings whose end_time is strictly before this instant are returned
     * @return list of (meeting id, tenant id) pairs for eligible meetings
     */
    List<MeetingIdAndTenant> findScheduledExpiredAcrossTenants(int batchSize, Instant cutoff);

    /** Lightweight pair returned by the cross-tenant expired-meetings query. */
    record MeetingIdAndTenant(UUID id, String tenantId) {}
}
