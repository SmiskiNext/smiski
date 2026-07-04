package io.github.smiskinext.meetingmanagement.infrastructure.sse.model;

import java.util.Objects;

/**
 * SSE event payload for join_request.approved events.
 * Sent to the guest SSE stream with LiveKit credentials.
 */
public record JoinRequestApprovedData(
        String requestId, String status, String liveKitToken, String roomName)
        implements SseEventData {
    public JoinRequestApprovedData {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(liveKitToken, "liveKitToken cannot be null");
        Objects.requireNonNull(roomName, "roomName cannot be null");
    }
}
