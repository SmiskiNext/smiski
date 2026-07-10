package io.github.smiskinext.record.domain;

import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Machine-readable error codes for the record bounded context, surfaced as the {@code code} member
 * of {@code application/problem+json} responses.
 */
public enum RecordErrorCode implements ErrorCode {
    INVALID_RECORDING_TRANSITION(ErrorCategory.CONFLICT),
    RECORDING_ALREADY_ACTIVE(ErrorCategory.CONFLICT),
    NO_ACTIVE_RECORDING(ErrorCategory.CONFLICT),
    RECORDING_NOT_FOUND(ErrorCategory.NOT_FOUND);

    private final ErrorCategory category;

    RecordErrorCode(ErrorCategory category) {
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
