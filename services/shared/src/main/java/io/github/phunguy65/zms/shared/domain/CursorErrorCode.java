package io.github.phunguy65.zms.shared.domain;

/**
 * Domain-layer error codes for cursor token operations.
 *
 * <p>Lives in the domain layer so that {@link CursorTokenEncoder} can reference it without
 * importing infrastructure types, preserving the hexagonal architecture dependency rule.
 */
public enum CursorErrorCode implements ErrorCode {

    /**
     * The provided cursor token is malformed, tampered with, or otherwise invalid.
     *
     * <p>Clients should treat this as "start from the beginning" and omit the {@code pageToken}
     * parameter on the next request.
     */
    INVALID_CURSOR
}
