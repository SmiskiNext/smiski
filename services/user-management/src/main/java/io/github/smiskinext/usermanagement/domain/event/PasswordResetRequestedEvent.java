package io.github.smiskinext.usermanagement.domain.event;

import io.github.smiskinext.usermanagement.domain.PublishableEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a password reset OTP is requested.
 * Topic: {@code user-management.password-reset.requested}.
 *
 * <p>The notification service consumes this event to send the OTP email.
 */
public record PasswordResetRequestedEvent(
        UUID eventId,
        UUID userId,
        String email,
        String fullName,
        String otp,
        Instant expiresAt,
        Instant requestedAt)
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
        return "io.github.phunguy65.zms.user.password-reset-requested.v1";
    }

    @Override
    public String topic() {
        return "user-management.password-reset.requested";
    }

    @Override
    public Instant occurredAt() {
        return requestedAt;
    }
}
