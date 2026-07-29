package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of an invitee tentatively responding: the full snapshot of the responding invitee.
 */
public record TentativeMeetingInviteeResult(
        UUID id,
        String accountId,
        String email,
        String displayName,
        String role,
        String status,
        Instant invitedAt,
        @Nullable Instant respondedAt) {}
