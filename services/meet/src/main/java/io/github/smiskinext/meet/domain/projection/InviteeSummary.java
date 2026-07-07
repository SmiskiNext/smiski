package io.github.smiskinext.meet.domain.projection;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record InviteeSummary(
        @Nullable String accountId,
        String email,
        @Nullable String displayName,
        String status,
        Instant invitedAt,
        @Nullable Instant respondedAt) {}
