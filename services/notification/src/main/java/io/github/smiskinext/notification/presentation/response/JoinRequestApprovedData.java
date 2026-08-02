package io.github.smiskinext.notification.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload pushed to a requester emitter as the {@code join_request_approved} SSE event, carrying
 * the LiveKit token and room name the requester needs to connect.
 */
@Schema(description = "Payload of the join_request_approved SSE event delivered to the requester")
public record JoinRequestApprovedData(
        @Schema(
                description = "LiveKit access token for connecting to the meeting room",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token")
        String token,

        @Schema(description = "LiveKit room name to connect to", example = "room-018f4e2a")
        String roomName) {}
