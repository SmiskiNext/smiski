package io.github.phunguy65.zms.shared.infrastructure.web;

import io.github.phunguy65.zms.shared.domain.ScrollParams;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Generic HTTP query-parameter object for cursor-based (keyset) scroll pagination.
 *
 * <p>Use this record directly when an endpoint only needs pagination (no domain-specific filters).
 * For endpoints that also carry filter fields, define a feature-specific record that implements
 * {@link ScrollParams} instead of wrapping this one.
 *
 * <p>Spring MVC binds query parameters automatically:
 *
 * <pre>{@code
 * GET /api/v1/items:search?size=20&pageToken=eyJ...&query=alice
 * }</pre>
 *
 * @param size          items per page, clamped to [1, 100] (default {@code 20})
 * @param pageTokenRaw  opaque cursor from the previous response's {@code nextPageToken}; absent on
 *                      first page
 * @param queryRaw      optional search string; semantics defined by the implementing use case
 */
public record ScrollRequest(
        @Min(1) @Max(100) int size,
        @Nullable String pageTokenRaw,
        @Nullable String queryRaw) implements ScrollParams {

    /** Default page size used when no {@code size} query parameter is supplied. */
    public static final int DEFAULT_SIZE = 20;

    /** Hard upper bound on page size to prevent runaway queries. */
    public static final int MAX_SIZE = 100;

    /**
     * Compact constructor – enforces invariants so callers never receive an invalid state.
     *
     * <ul>
     *   <li>{@code size} is clamped to [1, {@value MAX_SIZE}].
     * </ul>
     */
    public ScrollRequest {
        size = Math.clamp(size, 1, MAX_SIZE);
    }

    @Override
    public int pageSize() {
        return size;
    }

    @Override
    public Optional<String> pageToken() {
        return Optional.ofNullable(pageTokenRaw);
    }

    @Override
    public Optional<String> query() {
        return Optional.ofNullable(queryRaw);
    }

    /** Convenience factory with default pagination values and no token or query. */
    public static ScrollRequest defaults() {
        return new ScrollRequest(DEFAULT_SIZE, null, null);
    }
}
