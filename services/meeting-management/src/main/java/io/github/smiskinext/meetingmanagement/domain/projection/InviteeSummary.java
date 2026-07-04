package io.github.smiskinext.meetingmanagement.domain.projection;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record InviteeSummary(
        @Nullable UUID userId,
        String email,
        @Nullable String displayName,
        String status,
        Instant invitedAt,
        @Nullable Instant respondedAt) {}
