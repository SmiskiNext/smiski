package io.github.smiskinext.notification.presentation.response;

import org.jspecify.annotations.Nullable;

/**
 * Payload pushed to host emitters as the {@code join_request_created} SSE event.
 */
public record JoinRequestCreatedData(
        String requestId,
        String accountId,
        String displayName,
        @Nullable String avatarUrl) {}
