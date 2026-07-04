package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface ParticipatedMeetingRow {
    UUID getId();

    UUID getHostId();

    String getShortCode();

    @Nullable String getTitle();

    @Nullable String getDescription();

    @Nullable Instant getStartTime();

    @Nullable Instant getEndTime();

    String getType();

    String getStatus();

    String getSettings();

    Instant getCreatedAt();

    Instant getLastJoinedAt();
}
