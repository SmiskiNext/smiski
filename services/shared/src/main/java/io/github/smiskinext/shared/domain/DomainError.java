package io.github.smiskinext.shared.domain;

/**
 * Shared contract for domain-level errors returned via {@link Result}.
 *
 * <p>Sealed interfaces (e.g. {@code MeetingError}, {@code RecordError}) implement this interface so
 * that generic infrastructure helpers can build an RFC 9457 Problem Details body without
 * knowing the concrete error type.
 *
 * <p>A domain error is a pure value: it exposes a stable {@link ErrorCode} and the positional
 * arguments needed to interpolate its localized {@code detail} message. It deliberately carries no
 * human-readable text — that is resolved from a locale-specific message bundle at the boundary,
 * keeping the domain free of presentation and i18n concerns.
 */
public interface DomainError {

    /** The stable, machine-readable code identifying this error. */
    ErrorCode errorCode();

    /**
     * Positional arguments used to interpolate the localized {@code detail} message
     * (placeholders {@code {0}}, {@code {1}} …). Defaults to no arguments.
     */
    default Object[] messageArgs() {
        return new Object[0];
    }
}
