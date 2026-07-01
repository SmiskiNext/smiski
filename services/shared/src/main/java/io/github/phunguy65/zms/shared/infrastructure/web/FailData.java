package io.github.phunguy65.zms.shared.infrastructure.web;

import io.github.phunguy65.zms.shared.domain.ErrorCode;
import java.util.List;

/**
 * Canonical payload for all JSend {@code fail} responses.
 *
 * <p>All controllers and exception handlers must use this record as the {@code data} value passed
 * to {@link JsendResponse#fail(Object)}. This guarantees a single, consistent JSON shape:
 *
 * <pre>{@code
 * {
 *   "status": "fail",
 *   "data": {
 *     "message": "Human readable string",
 *     "code":    "ENUM_CONSTANT",
 *     "errors":  []
 *   }
 * }
 * }</pre>
 *
 * <p>For domain errors, pass {@link List#of()} as {@code errors}. For Bean Validation failures,
 * populate {@code errors} with one {@link Violation} per failing field.
 *
 * @param message human-readable summary of the failure
 * @param code    machine-readable error code (enables frontend i18n)
 * @param errors  field-level violations; never {@code null} — use an empty list for domain errors
 */
public record FailData(String message, ErrorCode code, List<Violation> errors) {}
