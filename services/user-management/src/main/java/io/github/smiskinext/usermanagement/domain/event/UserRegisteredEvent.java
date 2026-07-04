package io.github.smiskinext.usermanagement.domain.event;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published when a new user successfully registers. Topic:
 * {@code user-management.user.registered}.
 */
public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String fullName,
        @Nullable String username,
        Instant registeredAt)
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
        return "io.github.phunguy65.zms.user.registered.v1";
    }

    @Override
    public String topic() {
        return "user-management.user.registered";
    }

    @Override
    public Instant occurredAt() {
        return registeredAt;
    }
}
