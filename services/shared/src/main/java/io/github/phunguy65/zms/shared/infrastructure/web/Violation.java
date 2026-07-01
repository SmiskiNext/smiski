package io.github.phunguy65.zms.shared.infrastructure.web;

/**
 * Represents a single field-level validation failure inside a {@link FailData#errors()} list.
 *
 * @param field   the name of the request field that failed validation
 * @param message a human-readable description of why validation failed
 * @param code    machine-readable category code for frontend i18n
 */
public record Violation(String field, String message, ViolationCode code) {}
