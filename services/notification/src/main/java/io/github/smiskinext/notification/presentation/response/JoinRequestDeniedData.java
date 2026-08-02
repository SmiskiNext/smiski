package io.github.smiskinext.notification.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/**
 * Payload pushed to a requester emitter as the {@code join_request_denied} SSE event. Carries an
 * optional machine-readable reason and never a token.
 */
@Schema(description = "Payload of the join_request_denied SSE event delivered to the requester")
public record JoinRequestDeniedData(
        @Schema(
                description = "Machine-readable denial reason; null when no reason is provided",
                nullable = true)
        @Nullable String reason) {}
