package io.github.smiskinext.meet.application.result;

import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of a join attempt.
 *
 * <p>Under {@code ALLOW_ALL} the caller is admitted immediately: {@code status} is
 * {@link JoinRequestStatus#APPROVED} and {@code token} plus {@code roomName} carry the LiveKit
 * access details. Under {@code MANUAL_APPROVAL} a pending request is created: {@code status} is
 * {@link JoinRequestStatus#PENDING} and the LiveKit fields are {@code null}.
 */
public record RequestJoinResult(
        UUID requestId,
        JoinRequestStatus status,
        @Nullable String token,
        @Nullable String roomName) {

    public static RequestJoinResult approved(UUID requestId, String token, String roomName) {
        return new RequestJoinResult(requestId, JoinRequestStatus.APPROVED, token, roomName);
    }

    public static RequestJoinResult pending(UUID requestId) {
        return new RequestJoinResult(requestId, JoinRequestStatus.PENDING, null, null);
    }
}
