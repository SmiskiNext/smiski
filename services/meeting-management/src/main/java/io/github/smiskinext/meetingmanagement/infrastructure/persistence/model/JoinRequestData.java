package io.github.smiskinext.meetingmanagement.infrastructure.persistence.model;

import io.github.smiskinext.meetingmanagement.domain.model.JoinRequest;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import io.github.smiskinext.meetingmanagement.domain.model.valueobject.JoinRequestId;
import io.github.smiskinext.shared.domain.valueobject.MeetingId;
import io.github.smiskinext.shared.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis persistence model for JoinRequest.
 *
 * <p>Serialized as JSON string in Redis using Jackson.
 * Provides bidirectional mapping between domain model and persistence layer.
 */
public record JoinRequestData(
        String id,
        String meetingId,
        String userId,
        String displayName,
        String deviceId,
        String status,
        String requestedAt,
        String expiresAt) {
    /**
     * Compact constructor with minimal validation (null checks only).
     */
    public JoinRequestData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(meetingId, "meetingId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(requestedAt, "requestedAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    /**
     * Factory method: Domain model → Persistence model.
     */
    public static JoinRequestData from(JoinRequest domain) {
        return new JoinRequestData(
                domain.getId().value().toString(),
                domain.getMeetingId().value().toString(),
                domain.getUserId().map(u -> u.value().toString()).orElse(null),
                domain.getDisplayName(),
                domain.getDeviceId(),
                domain.getStatus().name(),
                domain.getRequestedAt().toString(),
                domain.getExpiresAt().toString());
    }

    /**
     * Convert to domain model.
     */
    public JoinRequest toDomain() {
        return JoinRequest.reconstitute(
                JoinRequestId.of(UUID.fromString(id)),
                MeetingId.of(UUID.fromString(meetingId)),
                userId != null ? UserId.of(UUID.fromString(userId)) : null,
                displayName,
                deviceId,
                JoinRequestStatus.valueOf(status),
                Instant.parse(requestedAt),
                Instant.parse(expiresAt));
    }
}
