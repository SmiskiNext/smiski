package io.github.smiskinext.notification.domain;

import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Machine-readable error codes for the notification bounded context, surfaced as the {@code code}
 * member of RFC 9457 Problem Details responses.
 */
public enum NotificationErrorCode implements ErrorCode {
    INVALID_SIGNATURE(ErrorCategory.VALIDATION);

    private final ErrorCategory category;

    NotificationErrorCode(ErrorCategory category) {
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
