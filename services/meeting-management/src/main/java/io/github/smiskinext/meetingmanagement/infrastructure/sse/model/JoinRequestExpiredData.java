package io.github.smiskinext.meetingmanagement.infrastructure.sse.model;

import java.util.Objects;

/**
 * SSE event payload for join_request.expired events.
 * Sent to both host (meeting-level) and guest (request-level) streams.
 */
public record JoinRequestExpiredData(String requestId, String status) implements SseEventData {
    public JoinRequestExpiredData {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
