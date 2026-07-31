package io.github.smiskinext.shared.infrastructure.web;

import io.github.smiskinext.shared.domain.ErrorCategory;
import io.github.smiskinext.shared.domain.ErrorCode;

/**
 * Cross-cutting error codes not tied to any single domain.
 *
 * <p>Service-specific codes live in their own enums (e.g. {@code MeetingErrorCode}). This enum
 * holds only infrastructure-level codes surfaced by {@link GlobalExceptionHandler} when translating
 * framework exceptions into {@code application/problem+json} responses.
 */
public enum CommonErrorCode implements ErrorCode {

    /** Umbrella code for Bean Validation failures; field details travel in the errors list. */
    VALIDATION_ERROR(ErrorCategory.VALIDATION),

    /** The request body could not be parsed (e.g. malformed JSON). */
    MALFORMED_REQUEST(ErrorCategory.VALIDATION),

    /** A required request parameter or path variable was missing. */
    MISSING_PARAMETER(ErrorCategory.VALIDATION),

    /** The HTTP method is not supported by the target resource. */
    METHOD_NOT_ALLOWED(ErrorCategory.METHOD_NOT_ALLOWED),

    /** The request payload media type is not supported. */
    UNSUPPORTED_MEDIA_TYPE(ErrorCategory.UNSUPPORTED_MEDIA_TYPE),

    /** No handler matched the requested resource. */
    RESOURCE_NOT_FOUND(ErrorCategory.NOT_FOUND),

    /** An unexpected, unclassified technical failure occurred. */
    INTERNAL_ERROR(ErrorCategory.INTERNAL),

    /** The caller lacks the required project permission for the operation. */
    NOT_AUTHORIZED(ErrorCategory.FORBIDDEN);

    private final ErrorCategory category;

    CommonErrorCode(ErrorCategory category) {
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
