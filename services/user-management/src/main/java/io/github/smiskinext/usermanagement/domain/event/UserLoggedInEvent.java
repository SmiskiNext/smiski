package io.github.smiskinext.usermanagement.domain.event;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/** Published when a user successfully logs in. Topic: {@code user-management.user.logged-in}. */
public record UserLoggedInEvent(UUID eventId, UUID userId, String email, Instant loginAt)
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
        return "io.github.phunguy65.zms.user.logged-in.v1";
    }

    @Override
    public String topic() {
        return "user-management.user.logged-in";
    }

    @Override
    public Instant occurredAt() {
        return loginAt;
    }
}
