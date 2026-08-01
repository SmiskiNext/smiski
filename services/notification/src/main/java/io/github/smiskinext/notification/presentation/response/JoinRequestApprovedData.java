package io.github.smiskinext.notification.presentation.response;

/**
 * Payload pushed to a requester emitter as the {@code join_request_approved} SSE event, carrying
 * the LiveKit token and room name the requester needs to connect.
 */
public record JoinRequestApprovedData(String token, String roomName) {}
