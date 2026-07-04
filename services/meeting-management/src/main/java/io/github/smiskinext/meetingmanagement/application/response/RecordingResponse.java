package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.RecordingStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RecordingResponse(
        UUID id,
        UUID meetingId,
        @Nullable String fileUrl,
        @Nullable String thumbnailUrl,
        RecordingStatus status,
        Instant startedAt,
        @Nullable Instant endedAt,
        int durationSeconds,
        long fileSizeBytes,
        Instant createdAt) {}
