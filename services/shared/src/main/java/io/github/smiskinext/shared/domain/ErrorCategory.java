package io.github.smiskinext.shared.domain;

/**
 * Framework-agnostic classification of an {@link ErrorCode}.
 *
 * <p>Lives in the domain layer so that error codes can declare their semantic category without
 * importing any HTTP or framework type. The infrastructure layer maps each category to a concrete
 * HTTP status when building an {@code application/problem+json} response.
 *
 * <p>The mapping to HTTP status is intentionally kept out of this enum to preserve the hexagonal
 * architecture rule that {@code shared.domain} must be usable without Spring on the classpath.
 */
public enum ErrorCategory {

    /** Request failed input validation (missing/invalid fields). Maps to HTTP 400. */
    VALIDATION,

    /** Authentication is missing or invalid. Maps to HTTP 401. */
    UNAUTHORIZED,

    /** Authenticated principal lacks permission for the operation. Maps to HTTP 403. */
    FORBIDDEN,

    /** The requested resource does not exist. Maps to HTTP 404. */
    NOT_FOUND,

    /** The HTTP method is not allowed for the target resource. Maps to HTTP 405. */
    METHOD_NOT_ALLOWED,

    /** The request conflicts with the current state of the resource. Maps to HTTP 409. */
    CONFLICT,

    /** The request payload media type is unsupported. Maps to HTTP 415. */
    UNSUPPORTED_MEDIA_TYPE,

    /** The request is well-formed but semantically invalid. Maps to HTTP 422. */
    UNPROCESSABLE,

    /** An upstream dependency is unavailable. Maps to HTTP 503. */
    UNAVAILABLE,

    /** An unexpected technical failure occurred. Maps to HTTP 500. */
    INTERNAL
}
