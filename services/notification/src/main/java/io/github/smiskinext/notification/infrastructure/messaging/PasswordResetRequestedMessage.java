package io.github.smiskinext.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka message for password reset requested events.
 * Maps to CloudEvent payload from user-management service.
 */
public record PasswordResetRequestedMessage(
        UUID eventId,
        UUID userId,
        String email,
        String fullName,
        String otp,
        Instant expiresAt,
        Instant requestedAt) {}
