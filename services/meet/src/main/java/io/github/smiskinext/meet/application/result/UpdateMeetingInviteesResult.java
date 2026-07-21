package io.github.smiskinext.meet.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of an invitee-list replacement: the current active invitee snapshots after synchronization.
 */
public record UpdateMeetingInviteesResult(List<Invitee> invitees) {

    public record Invitee(
            UUID id,
            String accountId,
            String email,
            String displayName,
            String role,
            String status,
            Instant invitedAt,
            @Nullable Instant respondedAt) {}
}
