package io.github.smiskinext.record.domain.projection;

import io.github.smiskinext.record.domain.model.RecordingStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RecordingSummary(
        UUID id,
        UUID meetingId,
        @Nullable String fileUrl,
        @Nullable String storagePath,
        @Nullable String thumbnailUrl,
        RecordingStatus status,
        Instant startedAt,
        @Nullable Instant endedAt,
        int durationSeconds,
        long fileSizeBytes,
        Instant createdAt) {}
