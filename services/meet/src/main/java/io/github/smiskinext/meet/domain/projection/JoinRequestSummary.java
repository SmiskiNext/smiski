package io.github.smiskinext.meet.domain.projection;

import io.github.smiskinext.meet.domain.model.JoinRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestSummary(
        UUID id,
        UUID meetingId,
        String accountId,
        String displayName,
        JoinRequestStatus status,
        Instant requestedAt,
        Instant expiresAt) {}
