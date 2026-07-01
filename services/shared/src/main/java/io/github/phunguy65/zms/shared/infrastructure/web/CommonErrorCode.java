package io.github.phunguy65.zms.shared.infrastructure.web;

import io.github.phunguy65.zms.shared.domain.ErrorCode;

/**
 * Shared error codes used across all services.
 *
 * <p>Service-specific codes live in their own enums (e.g. {@code AuthErrorCode}). This enum holds
 * only codes that are infrastructure-level and not tied to any single domain.
 */
public enum CommonErrorCode implements ErrorCode {

    /**
     * Umbrella code for Bean Validation failures.
     *
     * <p>Used by {@link GlobalExceptionHandler} as the {@code code} field in {@link FailData} when
     * one or more request fields fail {@code @Valid} constraints. Individual field details are
     * carried in the {@code errors} list as {@link Violation} records.
     */
    VALIDATION_ERROR,

    METHOD_NOT_ALLOWED
}
