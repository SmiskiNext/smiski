package io.github.smiskinext.usermanagement.domain.event;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a user's profile is replaced via the current-user update API.
 * Carries enough data for downstream services to update local projections without
 * HTTP callbacks. Topic: {@code user-management.user.updated}.
 *
 * <p>Schema version: {@code io.github.phunguy65.zms.user.updated.v1}. Adding fields is safe;
 * removing or renaming fields is a breaking change.
 */
public record UserUpdatedEvent(
        UUID eventId,
        UUID userId,
        String email,
        String fullName,
        @Nullable String username,
        @Nullable String avatarUrl,
        String authProvider,
        Instant updatedAt)
        implements PublishableEvent {

    @Override
    public UUID aggregateId() {
        return userId;
    }

    @Override
    public String aggregateType() {
        return "user";
    }

    @Override
    public String eventType() {
        return "io.github.phunguy65.zms.user.updated.v1";
    }

    @Override
    public String topic() {
        return "user-management.user.updated";
    }

    @Override
    public Instant occurredAt() {
        return updatedAt;
    }
}
