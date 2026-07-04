package io.github.smiskinext.usermanagement.domain.port;

import org.jspecify.annotations.Nullable;

/**
 * Filter criteria for keyset-scroll user search queries.
 *
 * <p>A single {@code query} string performs a case-insensitive OR substring match across both
 * {@code username} and {@code email}. A {@code null} query means no filter (return all active
 * users).
 *
 * @param query optional search string; {@code null} = no filter
 */
public record UserScrollFilter(@Nullable String query) {

    /** Returns a filter with no constraints (returns all active users). */
    public static UserScrollFilter empty() {
        return new UserScrollFilter(null);
    }

    /** Returns {@code true} if a search query is present. */
    public boolean hasQuery() {
        return query != null && !query.isBlank();
    }
}
