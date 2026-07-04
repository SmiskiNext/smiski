package io.github.smiskinext.meetingmanagement.infrastructure.sse;

/**
 * SSE event payload structure for Redis Pub/Sub broadcasting.
 *
 * @param type the event type (e.g., "join_request_created", "join_request_approved")
 * @param data the event data (arbitrary JSON object)
 */
public record SseEvent(String type, Object data) {}
