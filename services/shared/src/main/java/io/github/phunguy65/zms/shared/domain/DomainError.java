package io.github.phunguy65.zms.shared.domain;

/**
 * Shared contract for domain-level errors returned via {@link Result}.
 *
 * <p>Sealed interfaces (e.g. {@code AuthError}, {@code BookingError}) implement this interface so
 * that generic controller helpers can build a {@link
 * io.github.phunguy65.zms.shared.infrastructure.web.FailData} payload without knowing the
 * concrete error type.
 */
public interface DomainError {

    /** Human-readable description suitable for a JSend {@code fail} data payload. */
    String message();
}
