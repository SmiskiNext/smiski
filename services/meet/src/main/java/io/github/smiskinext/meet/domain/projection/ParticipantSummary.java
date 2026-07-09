package io.github.smiskinext.meet.domain.projection;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read-only projection for meeting participants list endpoints. */
public record ParticipantSummary(
        UUID id,
        UUID meetingId,
        String accountId,
        @Nullable String displayName,
        String role,
        Instant joinedAt,
        @Nullable Instant leftAt) {}
