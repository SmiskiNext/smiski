package io.github.smiskinext.usermanagement.domain.event;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user account is soft-deleted. Topic: {@code user-management.user.deleted}.
 */
public record UserDeletedEvent(UUID eventId, UUID userId, String email, Instant deletedAt)
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
        return "io.github.phunguy65.zms.user.deleted.v1";
    }

    @Override
    public String topic() {
        return "user-management.user.deleted";
    }

    @Override
    public Instant occurredAt() {
        return deletedAt;
    }
}
