package io.github.smiskinext.meetingmanagement.infrastructure.persistence.model;

import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestResult;
import io.github.smiskinext.meetingmanagement.domain.model.JoinRequestStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Redis persistence model for {@link JoinRequestResult}, serialized as JSON.
 *
 * <p>Holds the terminal outcome (token + room name on approval, optional reason on denial)
 * required to replay the resolution to a late-subscribing SSE client.
 */
public record JoinRequestResultData(
        String requestId,
        String status,
        @Nullable String liveKitToken,
        @Nullable String roomName,
        @Nullable String denyReason) {

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
