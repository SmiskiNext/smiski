package io.github.smiskinext.meet.infrastructure.persistence.model;

import io.github.smiskinext.meet.domain.model.JoinRequestResult;
import io.github.smiskinext.meet.domain.model.JoinRequestStatus;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Redis persistence model for {@link JoinRequestResult}.
 *
 * <p>Serialized as a JSON string in Redis so the terminal outcome of a decided join request can be
 * replayed to a late-subscribing requester. {@code liveKitToken} and {@code roomName} are populated
 * for {@code APPROVED} only; {@code denyReason} is reserved for future denial-reason support.
 */
public record JoinRequestResultData(
        String requestId,
        String status,
        @Nullable String liveKitToken,
        @Nullable String roomName,
        @Nullable String denyReason) {

    public JoinRequestResultData {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static JoinRequestResultData from(JoinRequestResult result) {
        return new JoinRequestResultData(
                result.requestId().toString(),
                result.status().name(),
                result.liveKitToken(),
                result.roomName(),
                result.denyReason());
    }

    public JoinRequestResult toDomain() {
        return new JoinRequestResult(
                UUID.fromString(requestId),
                JoinRequestStatus.valueOf(status),
                liveKitToken,
                roomName,
                denyReason);
    }
}
