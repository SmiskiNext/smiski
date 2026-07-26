package io.github.smiskinext.notification.infrastructure.persistence.model;

import io.github.smiskinext.notification.domain.model.PendingJoinRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Redis persistence model for {@link PendingJoinRequest}, serialized as a JSON string. All fields
 * are stored as strings so the document round-trips through the typed template without time-module
 * coupling.
 */
public record PendingJoinRequestData(
        String joinRequestId,
        String meetingId,
        String accountId,
        String displayName,
        String deviceId,
        @Nullable String avatarUrl,
        String expiresAt) {

    public PendingJoinRequestData {
        Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        Objects.requireNonNull(meetingId, "meetingId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static PendingJoinRequestData from(PendingJoinRequest request) {
        return new PendingJoinRequestData(
                request.joinRequestId().toString(),
                request.meetingId().toString(),
                request.accountId(),
                request.displayName(),
                request.deviceId(),
                request.avatarUrl(),
                request.expiresAt().toString());
    }

    public PendingJoinRequest toDomain() {
        return new PendingJoinRequest(
                UUID.fromString(joinRequestId),
                UUID.fromString(meetingId),
                accountId,
                displayName,
                deviceId,
                avatarUrl,
                Instant.parse(expiresAt));
    }
}
