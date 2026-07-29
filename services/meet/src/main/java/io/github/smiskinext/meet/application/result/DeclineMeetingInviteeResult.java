package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of an invitee declining their invitation: the full snapshot of the responding invitee.
 */
public record DeclineMeetingInviteeResult(
        UUID id,
        String accountId,
        String email,
        String displayName,
        String role,
        String status,
        Instant invitedAt,
        @Nullable Instant respondedAt) {}
