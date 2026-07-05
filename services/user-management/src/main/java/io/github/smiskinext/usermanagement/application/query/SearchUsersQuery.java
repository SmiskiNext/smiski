package io.github.smiskinext.usermanagement.application.query;

import io.github.smiskinext.shared.domain.ScrollParams;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Query object for searching users with keyset pagination.
 *
 * <p>Pure application-layer object — no HTTP/Spring binding annotations.
 * Cursor decoding is delegated to the caller (controller).
 *
 * @param queryValue    optional case-insensitive OR search across {@code username} and {@code email}
 * @param pageSize      items per page, clamped to [1, 100]
 * @param pageTokenValue opaque cursor from the previous response's {@code nextPageToken}; absent on first page
 */
public record SearchUsersQuery(
        @Nullable String queryValue, int pageSize, @Nullable String pageTokenValue)
        implements ScrollParams {

    public SearchUsersQuery {
        if (pageSize < 1) pageSize = 1;
        if (pageSize > 100) pageSize = 100;
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    @Override
    public Optional<String> pageToken() {
        return Optional.ofNullable(pageTokenValue);
    }

    @Override
    public Optional<String> query() {
        return Optional.ofNullable(queryValue);
    }
}
