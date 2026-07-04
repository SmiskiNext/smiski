package io.github.smiskinext.meetingmanagement.infrastructure.sse.model;

import java.util.Objects;

/**
 * SSE event payload for join_request.created events.
 * Broadcast to all hosts watching the meeting.
 */
public record JoinRequestCreatedData(String requestId, String meetingId, String displayName)
        implements SseEventData {
    public JoinRequestCreatedData {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(meetingId, "meetingId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
    }
}
