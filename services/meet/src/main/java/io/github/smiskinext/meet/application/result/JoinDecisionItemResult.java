package io.github.smiskinext.meet.application.result;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Framework-agnostic per-item result of a host accept/decline decision.
 *
 * <p>{@code token} and {@code roomName} are populated for {@link JoinDecisionStatus#APPROVED} only.
 * {@code reason} carries a machine-readable {@code MeetingErrorCode} name for
 * {@link JoinDecisionStatus#FAILED} only.
 */
public record JoinDecisionItemResult(
        UUID requestId,
        JoinDecisionStatus status,
        @Nullable String token,
        @Nullable String roomName,
        @Nullable String reason) {

    public static JoinDecisionItemResult approved(UUID requestId, String token, String roomName) {
        return new JoinDecisionItemResult(
                requestId, JoinDecisionStatus.APPROVED, token, roomName, null);
    }

    public static JoinDecisionItemResult denied(UUID requestId) {
        return new JoinDecisionItemResult(requestId, JoinDecisionStatus.DENIED, null, null, null);
    }

    public static JoinDecisionItemResult failed(UUID requestId, String reason) {
        return new JoinDecisionItemResult(requestId, JoinDecisionStatus.FAILED, null, null, reason);
    }
}
