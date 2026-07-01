package io.github.phunguy65.zms.shared.domain;

/**
 * Marker interface for machine-readable error codes used in JSend {@code fail} responses.
 *
 * <p>Each service defines its own enum implementing this interface (e.g. {@code AuthErrorCode}).
 * Constants are module-namespaced to prevent collisions. {@link io.github.phunguy65.zms.shared.infrastructure.web.CommonErrorCode} (in the shared
 * infrastructure layer) holds cross-cutting codes such as {@code VALIDATION_ERROR}.
 */
public interface ErrorCode {}
