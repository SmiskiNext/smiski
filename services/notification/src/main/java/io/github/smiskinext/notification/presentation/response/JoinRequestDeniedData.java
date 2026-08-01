package io.github.smiskinext.notification.presentation.response;

import org.jspecify.annotations.Nullable;

/**
 * Payload pushed to a requester emitter as the {@code join_request_denied} SSE event. Carries an
 * optional machine-readable reason and never a token.
 */
public record JoinRequestDeniedData(@Nullable String reason) {}
