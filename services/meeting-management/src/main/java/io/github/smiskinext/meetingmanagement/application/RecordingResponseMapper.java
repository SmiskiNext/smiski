package io.github.smiskinext.meetingmanagement.application;

import io.github.smiskinext.meetingmanagement.application.response.RecordingResponse;
import io.github.smiskinext.meetingmanagement.domain.model.Recording;
import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import io.github.smiskinext.meetingmanagement.domain.port.StoragePort;
import io.github.smiskinext.meetingmanagement.domain.projection.RecordingSummary;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class RecordingResponseMapper {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofHours(1);

    private final StoragePort storagePort;

    public RecordingResponseMapper(StoragePort storagePort) {
        this.storagePort = storagePort;
    }

    public RecordingResponse toResponse(Recording r) {
        return new RecordingResponse(
                r.getId().value(),
                r.getMeetingId().value(),
                resolveFileUrl(r.getStoragePath().orElse(null), r.getStatus()),
                r.getThumbnailUrl().orElse(null),
                r.getStatus(),
                r.getStartedAt(),
                r.getEndedAt().orElse(null),
                r.getDurationSeconds(),
                r.getFileSizeBytes(),
                r.getCreatedAt());
    }

    public RecordingResponse toResponse(RecordingSummary s) {
        return new RecordingResponse(
                s.id(),
                s.meetingId(),
                resolveFileUrl(s.storagePath(), s.status()),
                s.thumbnailUrl(),
                s.status(),
                s.startedAt(),
                s.endedAt(),
                s.durationSeconds(),
                s.fileSizeBytes(),
                s.createdAt());
    }

    private @Nullable String resolveFileUrl(@Nullable String storagePath, RecordingStatus status) {
        if (storagePath == null || status != RecordingStatus.COMPLETED) {
            return null;
        }
        return storagePort.generatePresignedUrl(storagePath, PRESIGNED_URL_EXPIRATION);
    }
}
