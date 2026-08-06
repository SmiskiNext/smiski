package io.github.smiskinext.shared.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * A single field-level validation failure inside the {@code errors} extension member of an
 * RFC 9457 Problem Details response.
 *
 * <p>{@code field} and {@code code} are stable and locale-independent, letting the client localize
 * on its own. {@code message} carries the server-localized text (resolved via {@code
 * Accept-Language}); it may be null when only a machine-readable signal is exposed.
 *
 * @param field   the request field that failed validation
 * @param code    machine-readable category of the failure, for client-side i18n
 * @param message server-localized human-readable description, or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Violation(
        String field, ViolationCode code, @Nullable String message) {}
