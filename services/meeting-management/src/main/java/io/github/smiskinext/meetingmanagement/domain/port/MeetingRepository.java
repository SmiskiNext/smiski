package io.github.smiskinext.meetingmanagement.domain.port;

import io.github.smiskinext.meetingmanagement.domain.model.Meeting;
import io.github.smiskinext.meetingmanagement.domain.model.MeetingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ParticipatedMeetingCursor;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.ShortCode;
import io.github.smiskinext.meetingmanagement.domain.projection.MeetingSummary;
import io.github.smiskinext.meetingmanagement.domain.projection.ParticipatedMeetingSummary;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import io.github.phunguy65.zms.shared.domain.ScrollCursor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface MeetingRepository {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(UUID id);

    /**
     * Finds a meeting by ID with a pessimistic write lock (SELECT FOR UPDATE).
     */
    Optional<Meeting> findByIdWithLock(UUID id);

    Optional<Meeting> findByShortCode(ShortCode shortCode);

    boolean existsByShortCode(ShortCode shortCode);

    CursorPageResponse<MeetingSummary> findSummariesByHostId(
            UUID hostId, @Nullable ScrollCursor cursor, int pageSize);

    CursorPageResponse<ParticipatedMeetingSummary> findParticipatedSummariesByUserId(
            UUID userId,
            Set<MeetingStatus> statuses,
            @Nullable ParticipatedMeetingCursor cursor,
            int pageSize);
}
