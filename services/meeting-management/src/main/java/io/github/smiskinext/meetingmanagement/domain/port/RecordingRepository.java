package io.github.smiskinext.meetingmanagement.domain.port;

import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.RecordingId;
import io.github.smiskinext.meetingmanagement.domain.projection.RecordingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.ScrollCursor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingRepository {

    Recording save(Recording recording);

    Optional<Recording> findById(RecordingId id);

    /**
     * Returns the recording currently in PENDING or RECORDING state for the given meeting.
     */
    Optional<Recording> findActiveByMeetingId(UUID meetingId);

    /**
     * Finds a recording by LiveKit egress ID.
     * Used by {@code egress_started} and {@code egress_ended} webhook handlers.
     */
    Optional<Recording> findByEgressId(LiveKitEgressId egressId);

    List<Recording> findPendingCreatedBefore(Instant cutoff);

    List<Recording> findByMeetingId(UUID meetingId);

    /**
     * Keyset-scroll recordings for a meeting, ordered by (created_at DESC, id DESC).
     */
    CursorPageResponse<Recording> findByMeetingIdKeyset(
            UUID meetingId, ScrollCursor cursor, int pageSize);

    CursorPageResponse<RecordingSummary> findSummariesByMeetingId(
            UUID meetingId, ScrollCursor cursor, int pageSize);

    List<RecordingSummary> findCompletedSummariesByMeetingId(UUID meetingId);
}
