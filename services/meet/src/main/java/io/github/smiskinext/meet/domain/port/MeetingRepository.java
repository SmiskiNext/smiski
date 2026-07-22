package io.github.smiskinext.meet.domain.port;

import io.github.smiskinext.meet.domain.model.Meeting;
import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.meet.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meet.domain.projection.MeetingSearchCriteria;
import io.github.smiskinext.meet.domain.projection.MeetingSummary;
import io.github.smiskinext.meet.domain.projection.ParticipatedMeetingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
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
     * Finds a meeting by ID with a pessimistic write lock (SELECT FOR UPDATE).
     */
    Optional<Meeting> findByIdWithLock(UUID id);

    /**
     * Finds an active (not soft-deleted) meeting by ID with a pessimistic write lock. Soft-deleted
     * and unknown meetings both return an empty result, so callers cannot distinguish them.
     */
    Optional<Meeting> findActiveByIdWithLock(UUID id);

    Optional<Meeting> findByShortCode(ShortCode shortCode);

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
}
