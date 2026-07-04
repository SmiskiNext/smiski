package io.github.smiskinext.meetingmanagement.domain.projection;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read-only projection for meeting participants list endpoints. */
public record ParticipantSummary(
        Long id,
        UUID meetingId,
        @Nullable UUID userId,
        String displayName,
        String role,
        Instant joinedAt,
        @Nullable Instant leftAt) {}
