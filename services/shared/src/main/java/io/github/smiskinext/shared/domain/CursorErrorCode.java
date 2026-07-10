package io.github.smiskinext.shared.domain;

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
    INVALID_CURSOR(ErrorCategory.VALIDATION);

    private final ErrorCategory category;

    CursorErrorCode(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
