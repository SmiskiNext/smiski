package io.github.phunguy65.zms.shared.domain;

import java.util.Optional;

/**
 * Marker interface for keyset (cursor) scroll pagination request parameters.
 *
 * <p>Implement this interface on any
 * request DTO that carries {@code pageSize}, {@code pageToken}, and optional {@code query}
 * parameters for cursor-based scrolling.
 *
 * <p>Example usage in a feature module:
 *
 * <pre>{@code
 * public record SearchUsersRequest(
 *         Integer size,
 *         String pageToken,
 *         String query) implements ScrollParams {
 *     public int pageSize() { return size; }
 *     public Optional<String> pageToken() { return Optional.ofNullable(pageToken); }
 *     public Optional<String> query() { return Optional.ofNullable(query); }
 * }
 * }</pre>
 *
 * <p>Pure Java interface – zero framework dependencies.
 */
public interface ScrollParams {

    /** Number of items per page. Must be in range [1, 100]. */
    int pageSize();

    /**
     * Opaque cursor token from the previous response's {@code nextPageToken}.
     * Returns empty when fetching the first page.
     */
    Optional<String> pageToken();

    /**
     * Optional search query string. Semantics are defined by the implementing use case
     * (e.g. OR match across username and email).
     * Returns empty when no search filter is requested.
     */
    Optional<String> query();
}
