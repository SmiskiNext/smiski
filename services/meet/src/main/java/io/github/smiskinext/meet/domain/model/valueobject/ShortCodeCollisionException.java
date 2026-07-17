package io.github.smiskinext.meet.domain.model.valueobject;

/**
 * Signals that a generated {@link ShortCode} collided with an existing meeting code at persistence
 * time (unique-constraint violation).
 *
 * <p>This is a transient, retryable condition, not a business error surfaced to callers. The
 * persistence adapter raises it when the short-code unique index rejects an insert; the
 * collision-retry policy in the application layer catches it and retries with a fresh code.
 */
public final class ShortCodeCollisionException extends RuntimeException {

    public ShortCodeCollisionException(String shortCode, Throwable cause) {
        super("Short code already in use: " + shortCode, cause);
    }
}
