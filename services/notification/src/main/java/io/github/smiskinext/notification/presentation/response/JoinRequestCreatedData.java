package io.github.smiskinext.notification.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/**
 * Payload pushed to host emitters as the {@code join_request_created} SSE event.
 */
@Schema(description = "Payload of the join_request_created SSE event delivered to the meeting host")
public record JoinRequestCreatedData(
        @Schema(
                description = "Identifier of the join request",
                example = "018f4e2a-1b3c-7d8e-9f0a-1b2c3d4e5f6a")
        String requestId,

        @Schema(
                description = "Account identifier of the requester",
                example = "018f4e2a-0000-7d8e-9f0a-1b2c3d4e5f6a")
        String accountId,

        @Schema(description = "Display name provided by the requester", example = "Alice")
        String displayName,

        @Schema(
                description = "Avatar URL provided by the requester; null when not supplied",
                nullable = true)
        @Nullable String avatarUrl) {}
