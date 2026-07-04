package io.github.smiskinext.meetingmanagement.infrastructure.sse.model;

import java.util.Objects;

/**
 * SSE event payload for join_request.denied events.
 * Sent to the guest SSE stream when host rejects the request.
 */
public record JoinRequestDeniedData(String requestId, String status) implements SseEventData {
    public JoinRequestDeniedData {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
