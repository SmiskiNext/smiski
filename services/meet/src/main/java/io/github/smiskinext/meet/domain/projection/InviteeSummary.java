package io.github.smiskinext.meet.domain.projection;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record InviteeSummary(
        String accountId,
        String email,
        String displayName,
        String status,
        Instant invitedAt,
        @Nullable Instant respondedAt) {}
