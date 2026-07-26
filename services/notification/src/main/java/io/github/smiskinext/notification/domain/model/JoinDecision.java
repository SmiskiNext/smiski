package io.github.smiskinext.notification.domain.model;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A host's terminal decision on a join request, retained by the notification service so a requester
 * that subscribes after the decision arrives can still be shown the recorded outcome.
 *
 * <p>Framework-agnostic: carries only the scalar fields the requester-facing stream needs. An
 * {@code APPROVED} decision retains the LiveKit {@code token} and {@code roomName}; a {@code DENIED}
 * decision retains neither and may carry an optional {@code reason}.
 */
public record JoinDecision(
        UUID joinRequestId,
        Status status,
        @Nullable String token,
        @Nullable String roomName,
        @Nullable String reason) {

    /** Terminal decision outcomes delivered to a requester. */
    public enum Status {
        APPROVED,
        DENIED
    }

    public JoinDecision {
        Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static JoinDecision approved(UUID joinRequestId, String token, String roomName) {
        return new JoinDecision(
                joinRequestId,
                Status.APPROVED,
                Objects.requireNonNull(token, "token must not be null"),
                Objects.requireNonNull(roomName, "roomName must not be null"),
                null);
    }

    public static JoinDecision denied(UUID joinRequestId, @Nullable String reason) {
        return new JoinDecision(joinRequestId, Status.DENIED, null, null, reason);
    }
}
