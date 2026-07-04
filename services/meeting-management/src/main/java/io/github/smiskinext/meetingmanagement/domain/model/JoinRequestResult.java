package io.github.smiskinext.meetingmanagement.domain.model;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Terminal outcome of a join request, persisted so that a late-subscribing SSE client can replay
 * the resolution.
 *
 * <p>Only terminal statuses are valid: {@code APPROVED}, {@code DENIED}, {@code EXPIRED}.
 *
 * <p>{@code liveKitToken} and {@code roomName} are populated for {@code APPROVED} only.
 * {@code denyReason} is reserved for future denial-reason support.
 */
public record JoinRequestResult(
        UUID requestId,
        JoinRequestStatus status,
        @Nullable String liveKitToken,
        @Nullable String roomName,
        @Nullable String denyReason) {

    public JoinRequestResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("status must be terminal: " + status);
        }
    }

    public static JoinRequestResult approved(UUID requestId, String liveKitToken, String roomName) {
        return new JoinRequestResult(
                requestId,
                JoinRequestStatus.APPROVED,
                Objects.requireNonNull(liveKitToken, "liveKitToken"),
                Objects.requireNonNull(roomName, "roomName"),
                null);
    }

    public static JoinRequestResult denied(UUID requestId, @Nullable String denyReason) {
        return new JoinRequestResult(requestId, JoinRequestStatus.DENIED, null, null, denyReason);
    }

    public static JoinRequestResult expired(UUID requestId) {
        return new JoinRequestResult(requestId, JoinRequestStatus.EXPIRED, null, null, null);
    }
}
