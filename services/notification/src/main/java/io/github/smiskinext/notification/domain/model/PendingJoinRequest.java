package io.github.smiskinext.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A join request awaiting host action, retained by the notification service so a host that
 * subscribes after the request arrives can still be shown the current pending set.
 *
 * <p>Framework-agnostic: carries only the scalar fields decoded from the {@code meet.join.created}
 * event needed for host display and replay.
 */
public record PendingJoinRequest(
        UUID joinRequestId,
        UUID meetingId,
        String accountId,
        String displayName,
        String deviceId,
        @Nullable String avatarUrl,
        Instant expiresAt) {

    public PendingJoinRequest {
        Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        Objects.requireNonNull(meetingId, "meetingId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
