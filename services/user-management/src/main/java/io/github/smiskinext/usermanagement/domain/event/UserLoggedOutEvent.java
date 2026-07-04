package io.github.smiskinext.usermanagement.domain.event;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/** Published when a user successfully logs out. Topic: {@code user-management.user.logged-out}. */
public record UserLoggedOutEvent(UUID eventId, UUID userId, Instant logoutAt)
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
        return "io.github.phunguy65.zms.user.logged-out.v1";
    }

    @Override
    public String topic() {
        return "user-management.user.logged-out";
    }

    @Override
    public Instant occurredAt() {
        return logoutAt;
    }
}
