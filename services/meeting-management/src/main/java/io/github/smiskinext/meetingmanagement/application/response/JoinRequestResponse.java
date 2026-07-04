package io.github.smiskinext.meetingmanagement.application.response;

import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Response DTO for a join request in the pending queue.
 */
public record JoinRequestResponse(
        UUID id,
        UUID meetingId,
        @Nullable UUID userId,
        String displayName,
        @Nullable String avatarUrl,
        JoinRequestStatus status,
        Instant requestedAt,
        Instant expiresAt) {}
