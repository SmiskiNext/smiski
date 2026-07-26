package io.github.smiskinext.meet.infrastructure.persistence.model;

import io.github.smiskinext.meet.domain.model.JoinRequest;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import io.github.smiskinext.meet.domain.model.valueobject.AccountId;
import io.github.smiskinext.meet.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.meet.domain.model.valueobject.MeetingId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Redis persistence model for {@link JoinRequest}.
 *
 * <p>Serialized as a JSON string in Redis. All fields are stored as strings so the same document is
 * both written by the atomic Lua {@code SET} and read back through the typed template without
 * time-module coupling.
 */
public record JoinRequestData(
        String id,
        String meetingId,
        String accountId,
        String displayName,
        String deviceId,
        @Nullable String avatarUrl,
        String status,
        String requestedAt,
        String expiresAt) {

    public JoinRequestData {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(meetingId, "meetingId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static JoinRequestData from(JoinRequest request) {
        return new JoinRequestData(
                request.getId().value().toString(),
                request.getMeetingId().value().toString(),
                request.getAccountId().value(),
                request.getDisplayName(),
                request.getDeviceId(),
                request.getAvatarUrl(),
                request.getStatus().name(),
                request.getRequestedAt().toString(),
                request.getExpiresAt().toString());
    }

    public JoinRequest toDomain() {
        return JoinRequest.reconstitute(
                JoinRequestId.of(UUID.fromString(id)),
                MeetingId.of(UUID.fromString(meetingId)),
                AccountId.of(accountId),
                displayName,
                deviceId,
                avatarUrl,
                JoinRequestStatus.valueOf(status),
                Instant.parse(requestedAt),
                Instant.parse(expiresAt));
    }
}
