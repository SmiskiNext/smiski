package io.github.smiskinext.notification.infrastructure.persistence.model;

import io.github.smiskinext.notification.domain.model.JoinDecision;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Redis persistence model for {@link JoinDecision}, serialized as a JSON string. {@code token} and
 * {@code roomName} are populated for {@code APPROVED} only; {@code reason} is optional and reserved
 * for denials.
 */
public record JoinDecisionData(
        String joinRequestId,
        String status,
        @Nullable String token,
        @Nullable String roomName,
        @Nullable String reason) {

    public JoinDecisionData {
        Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static JoinDecisionData from(JoinDecision decision) {
        return new JoinDecisionData(
                decision.joinRequestId().toString(),
                decision.status().name(),
                decision.token(),
                decision.roomName(),
                decision.reason());
    }

    public JoinDecision toDomain() {
        return new JoinDecision(
                UUID.fromString(joinRequestId),
                JoinDecision.Status.valueOf(status),
                token,
                roomName,
                reason);
    }
}
