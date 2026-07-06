package io.github.smiskinext.meet.domain.projection;

import io.github.smiskinext.meet.domain.model.MeetingStatus;
import io.github.smiskinext.meet.domain.model.MeetingType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MeetingSummary(
        UUID id,
        UUID hostId,
        String shortCode,
        @Nullable String title,
        @Nullable String description,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        MeetingType type,
        MeetingStatus status,
        MeetingSettingsSummary settings,
        Instant createdAt) {}
