package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitEgressId;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.LiveKitRoomName;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.RecordingId;
import io.github.smiskinext.meetingmanagement.domain.port.RecordingRepository;
import io.github.smiskinext.meetingmanagement.domain.projection.RecordingSummary;
import io.github.smiskinext.shared.domain.CursorPageResponse;
import io.github.smiskinext.shared.domain.ScrollCursor;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RecordingRepositoryAdapter implements RecordingRepository {

    private final RecordingJpaRepository jpa;

    public RecordingRepositoryAdapter(RecordingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Recording save(Recording recording) {
        jpa.save(toEntity(recording));
        return recording;
    }

    @Override
    public Optional<Recording> findById(RecordingId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Recording> findActiveByMeetingId(UUID meetingId) {
        return jpa.findByMeetingIdAndStatusIn(
                        meetingId,
                        List.of(RecordingStatus.PENDING.name(), RecordingStatus.RECORDING.name()))
                .map(this::toDomain);
    }

    @Override
    public Optional<Recording> findByEgressId(LiveKitEgressId egressId) {
        return jpa.findByLivekitEgressId(egressId.value()).map(this::toDomain);
    }

    @Override
    public List<Recording> findPendingCreatedBefore(Instant cutoff) {
        return jpa.findByStatusAndCreatedAtBefore(RecordingStatus.PENDING.name(), cutoff).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Recording> findByMeetingId(UUID meetingId) {
        return jpa.findByMeetingId(meetingId).stream().map(this::toDomain).toList();
    }

    @Override
    public CursorPageResponse<Recording> findByMeetingIdKeyset(
            UUID meetingId, ScrollCursor cursor, int pageSize) {
        int fetchLimit = pageSize + 1;
        var cursorCreatedAt = cursor != null ? cursor.createdAt() : null;
        var cursorId = cursor != null ? cursor.id().toString() : null;

        List<RecordingJpaEntity> rows = jpa.findByMeetingIdKeyset(
                meetingId.toString(), cursorCreatedAt, cursorId, fetchLimit);

        boolean hasNext = rows.size() > pageSize;
        List<Recording> items =
                rows.stream().limit(pageSize).map(this::toDomain).toList();
        return CursorPageResponse.of(items, pageSize, hasNext);
    }

    @Override
    public CursorPageResponse<RecordingSummary> findSummariesByMeetingId(
            UUID meetingId, ScrollCursor cursor, int pageSize) {
        int fetchLimit = pageSize + 1;
        var cursorCreatedAt = cursor != null ? cursor.createdAt() : null;
        var cursorId = cursor != null ? cursor.id().toString() : null;

        List<RecordingJpaEntity> rows = jpa.findByMeetingIdKeyset(
                meetingId.toString(), cursorCreatedAt, cursorId, fetchLimit);

        boolean hasNext = rows.size() > pageSize;
        List<RecordingSummary> items =
                rows.stream().limit(pageSize).map(this::toSummary).toList();
        return CursorPageResponse.of(items, pageSize, hasNext);
    }

    @Override
    public List<RecordingSummary> findCompletedSummariesByMeetingId(UUID meetingId) {
        return jpa
                .findByMeetingIdAndStatusOrderByCreatedAtDescIdDesc(
                        meetingId, RecordingStatus.COMPLETED.name())
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private Recording toDomain(RecordingJpaEntity e) {
        return Recording.reconstitute(
                RecordingId.of(e.getId()),
                MeetingId.of(e.getMeetingId()),
                e.getLivekitRoomName() != null
                        ? LiveKitRoomName.of(e.getLivekitRoomName())
                        : LiveKitRoomName.fromMeetingId(MeetingId.of(e.getMeetingId())),
                e.getLivekitEgressId() != null ? LiveKitEgressId.of(e.getLivekitEgressId()) : null,
                e.getFileUrl(),
                e.getThumbnailUrl(),
                e.getStoragePath(),
                e.getErrorMessage(),
                RecordingStatus.valueOf(e.getStatus()),
                e.getStartedAt(),
                e.getEndedAt(),
                e.getDurationSeconds(),
                e.getFileSizeBytes(),
                e.getCreatedAt());
    }

    private RecordingSummary toSummary(RecordingJpaEntity e) {
        return new RecordingSummary(
                e.getId(),
                e.getMeetingId(),
                e.getFileUrl(),
                e.getStoragePath(),
                e.getThumbnailUrl(),
                RecordingStatus.valueOf(e.getStatus()),
                e.getStartedAt(),
                e.getEndedAt(),
                e.getDurationSeconds(),
                e.getFileSizeBytes(),
                e.getCreatedAt());
    }

    private RecordingJpaEntity toEntity(Recording r) {
        return new RecordingJpaEntity(
                r.getId().value(),
                r.getMeetingId().value(),
                r.getLivekitEgressId().map(LiveKitEgressId::value).orElse(null),
                r.getLivekitRoomName().value(),
                r.getFileUrl().orElse(null),
                r.getThumbnailUrl().orElse(null),
                r.getStoragePath().orElse(null),
                r.getStatus().name(),
                r.getStartedAt(),
                r.getEndedAt().orElse(null),
                r.getDurationSeconds(),
                r.getFileSizeBytes(),
                r.getErrorMessage().orElse(null),
                r.getCreatedAt());
    }
}
