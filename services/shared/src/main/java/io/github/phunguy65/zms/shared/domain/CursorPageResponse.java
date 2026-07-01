package io.github.phunguy65.zms.shared.domain;

import java.util.List;

/**
 * A page of keyset-scrolled results with a flag indicating whether more pages exist.
 *
 * <p>Pure Java domain abstraction for cursor-based pagination.
 *
 * <p>Infrastructure adapters build this record after fetching {@code size + 1} rows: if the extra
 * row exists, {@code hasNext} is {@code true}; otherwise it is {@code false} (end of results).
 * The use case layer is responsible for encoding the actual cursor token from the last domain item.
 *
 * @param <T> the domain type for each item in the page
 */
public record CursorPageResponse<T>(List<T> items, int pageSize, boolean hasNext) {

    /** Compact canonical constructor – defensively copies {@code items} to ensure immutability. */
    public CursorPageResponse {
        items = List.copyOf(items);
    }

    /**
     * Factory for infrastructure adapters.
     *
     * @param items    already-mapped domain items (must NOT include the extra probe row)
     * @param pageSize requested page size
     * @param hasNext  {@code true} if more pages exist (detected by fetching size+1 rows)
     */
    public static <T> CursorPageResponse<T> of(List<T> items, int pageSize, boolean hasNext) {
        return new CursorPageResponse<>(items, pageSize, hasNext);
    }

    /** Convenience factory for an empty result set (first page, no results). */
    public static <T> CursorPageResponse<T> empty(int pageSize) {
        return new CursorPageResponse<>(List.of(), pageSize, false);
    }
}
